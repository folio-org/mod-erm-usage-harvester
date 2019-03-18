package org.olf.erm.usage.harvester.endpoints;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.CustomerReference;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.Range;
import org.niso.schemas.sushi.ReportDefinition;
import org.niso.schemas.sushi.ReportDefinition.Filters;
import org.niso.schemas.sushi.ReportRequest;
import org.niso.schemas.sushi.Requestor;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.Counter4Utils;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import sushiservice.SushiService;
import sushiservice.SushiServiceInterface;

public class CS41Impl implements ServiceEndpoint {

  private UsageDataProvider provider;
  private SushiServiceInterface port;

  public ReportRequest createReportRequest(String report, String beginDate, String endDate) {
    Requestor requestor = new Requestor();
    requestor.setID(provider.getSushiCredentials().getRequestorId());
    // requestor.setName(provider.getRequestorName());
    // requestor.setEmail(provider.getRequestorMail());

    CustomerReference ref = new CustomerReference();
    ref.setID(provider.getSushiCredentials().getCustomerId());

    ReportDefinition rep = new ReportDefinition();
    rep.setName(report);
    rep.setRelease(provider.getHarvestingConfig().getReportRelease().toString());
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
    bindingProvider
        .getRequestContext()
        .put(
            BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
            provider.getHarvestingConfig().getSushiConfig().getServiceUrl());
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    Future<String> future = Future.future();

    Vertx.currentContext()
        .executeBlocking(
            block -> {
              CounterReportResponse counterReportResponse;
              try {
                ReportRequest reportRequest = createReportRequest(report, beginDate, endDate);
                counterReportResponse = port.getReport(reportRequest);
              } catch (java.lang.Exception e) {
                String messages =
                    ExceptionUtils.getThrowableList(e)
                        .stream()
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining(", "));
                block.fail("Error getting report: " + messages);
                return;
              }

              List<Exception> exceptions = Counter4Utils.getExceptions(counterReportResponse);
              if (exceptions.isEmpty()
                  && counterReportResponse.getReport() != null
                  && !counterReportResponse.getReport().getReport().isEmpty()) {
                Report reportResult = counterReportResponse.getReport().getReport().get(0);
                block.complete(Counter4Utils.toJSON(reportResult));
              } else {
                block.fail("Report not valid: " + Counter4Utils.getErrorMessages(exceptions));
              }
            },
            future.completer());

    return future;
  }

  @Override
  public boolean isValidReport(String report) {
    // TODO Auto-generated method stub
    return false;
  }
}
