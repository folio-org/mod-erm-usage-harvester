package org.olf.erm.usage.harvester.endpoints;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import org.apache.log4j.Logger;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.CustomerReference;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.ExceptionSeverity;
import org.niso.schemas.sushi.Range;
import org.niso.schemas.sushi.ReportDefinition;
import org.niso.schemas.sushi.ReportDefinition.Filters;
import org.niso.schemas.sushi.ReportRequest;
import org.niso.schemas.sushi.Requestor;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import sushiservice.SushiService;
import sushiservice.SushiServiceInterface;

public class CS41Impl implements ServiceEndpoint {

  private UsageDataProvider provider;
  private SushiServiceInterface port;

  private static final Logger LOG = Logger.getLogger(CS41Impl.class);

  public ReportRequest createReportRequest(String report, String beginDate, String endDate) {
    Requestor requestor = new Requestor();
    requestor.setID(provider.getRequestorId());
    // requestor.setName(provider.getRequestorName());
    // requestor.setEmail(provider.getRequestorMail());

    CustomerReference ref = new CustomerReference();
    ref.setID(provider.getCustomerId());

    ReportDefinition rep = new ReportDefinition();
    rep.setName(report);
    rep.setRelease(provider.getReportRelease().toString());
    Filters filter = new Filters();
    Range range = new Range();
    try {
      range.setBegin(DatatypeFactory.newInstance().newXMLGregorianCalendar(beginDate));
      range.setEnd(DatatypeFactory.newInstance().newXMLGregorianCalendar(endDate));
    } catch (DatatypeConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    filter.setUsageDateRange(range);
    rep.setFilters(filter);

    ReportRequest request = new ReportRequest();
    request.setRequestor(requestor);
    request.setCustomerReference(ref);
    request.setReportDefinition(rep);

    return request;
  }

  public CS41Impl(UsageDataProvider provider) {
    this.provider = provider;

    SushiService service = new SushiService();
    QName next = service.getPorts().next();
    port = service.getPort(next, SushiServiceInterface.class);
    BindingProvider bindingProvider = (BindingProvider) port;
    bindingProvider.getRequestContext()
        .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, "http://www.jstor.org/sushi");
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    Future<String> future = Future.future();

    // TODO: blocking
    Vertx.currentContext().executeBlocking(block -> {
      ReportRequest reportRequest = createReportRequest(report, beginDate, endDate);
      CounterReportResponse counterReportResponse = port.getReport(reportRequest);
      Report reportResult = counterReportResponse.getReport().getReport().get(0);

      List<Exception> exceptions = counterReportResponse.getException()
          .stream()
          .filter(e -> e.getSeverity().equals(ExceptionSeverity.ERROR)
              || e.getSeverity().equals(ExceptionSeverity.FATAL))
          .collect(Collectors.toList());
      if (exceptions.isEmpty()) {
        block.complete(Tool.toJSON(reportResult));
      } else {
        block.fail("Report not valid: "
            + exceptions.stream().map(Exception::getMessage).collect(Collectors.joining(", ")));
      }
    }, handler -> {
      if (handler.succeeded()) {
        future.complete(handler.result().toString());
      }
    });

    return future;
  }

  @Override
  public boolean isValidReport(String report) {
    // TODO Auto-generated method stub
    return false;
  }

}
