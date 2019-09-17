package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sushiservice.SushiService;
import sushiservice.SushiServiceInterface;

public class CS41Impl implements ServiceEndpoint {

  private UsageDataProvider provider;
  private SushiServiceInterface port;
  private static final Logger LOG = LoggerFactory.getLogger(CS41Impl.class);

  private ReportRequest createReportRequest(String report, String beginDate, String endDate) {
    Requestor requestor = new Requestor();
    requestor.setID(provider.getSushiCredentials().getRequestorId());
    requestor.setName(
        ObjectUtils.defaultIfNull(provider.getSushiCredentials().getRequestorName(), ""));
    requestor.setEmail(
        ObjectUtils.defaultIfNull(provider.getSushiCredentials().getRequestorMail(), ""));

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
      LOG.error(e.getMessage(), e);
    }
    filter.setUsageDateRange(range);
    rep.setFilters(filter);

    ReportRequest request = new ReportRequest();
    request.setRequestor(requestor);
    request.setCustomerReference(ref);
    request.setReportDefinition(rep);

    return request;
  }

  CS41Impl(UsageDataProvider provider) {
    this.provider = provider;

    SushiService service = new SushiService();
    QName next = service.getPorts().next();
    port = service.getPort(next, SushiServiceInterface.class);

    String serviceUrl = provider.getHarvestingConfig().getSushiConfig().getServiceUrl();

    try {
      getProxy(new URI(serviceUrl))
          .ifPresent(
              p -> {
                InetSocketAddress addr = (InetSocketAddress) p.address();
                Client client = ClientProxy.getClient(port);
                HTTPConduit http = (HTTPConduit) client.getConduit();
                http.getClient().setProxyServer(addr.getHostString());
                http.getClient().setProxyServerPort(addr.getPort());
              });
    } catch (URISyntaxException e) {
      LOG.error("Error getting proxy: {}", e.getMessage());
    }

    BindingProvider bindingProvider = (BindingProvider) port;
    bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, serviceUrl);
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    Future<String> future = Future.future();

    Context context = Vertx.currentContext();
    if (context == null) context = Vertx.vertx().getOrCreateContext();

    context.executeBlocking(
        block -> {
          CounterReportResponse counterReportResponse;
          try {
            ReportRequest reportRequest = createReportRequest(report, beginDate, endDate);
            counterReportResponse = port.getReport(reportRequest);
          } catch (java.lang.Exception e) {
            String messages =
                ExceptionUtils.getThrowableList(e).stream()
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
    return false;
  }
}
