package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.xml.ws.BindingProvider;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.HTTPConduit;
import org.folio.rest.jaxrs.model.CounterReport;
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
import org.olf.erm.usage.counter41.Counter4Utils.ReportSplitException;
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
      range.setBegin(LocalDate.parse(beginDate));
      range.setEnd(LocalDate.parse(endDate));
    } catch (DateTimeParseException e) {
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

  public CS41Impl(UsageDataProvider provider) {
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

    Client client = ClientProxy.getClient(port);
    HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
    httpConduit.getClient().setAutoRedirect(true);

    client
        .getInInterceptors()
        .add(
            new AbstractPhaseInterceptor<Message>(Phase.READ) {
              @Override
              public void handleMessage(Message message) {
                int statusCode = (int) message.get(Message.RESPONSE_CODE);
                Map<String, List<String>> headers =
                    CastUtils.cast((Map) message.get(Message.PROTOCOL_HEADERS));

                if (statusCode / 100 != 2) {
                  String errMessage =
                      String.format(
                          "Server responded with status code %s, headers: %s", statusCode, headers);
                  throw new Fault(new java.lang.Exception(errMessage));
                }
              }
            });
    client.getInFaultInterceptors().add(new LoggingInInterceptor());
  }

  private List<CounterReport> createCounterReportList(
      Report report, String reportType, UsageDataProvider provider) throws ReportSplitException {
    List<Report> splitReports = Counter4Utils.split(report);
    return splitReports.stream()
        .map(
            r -> {
              List<YearMonth> yearMonthsFromReport = Counter4Utils.getYearMonthsFromReport(r);
              if (yearMonthsFromReport.size() != 1) {
                throw new CS41Exception("Split report size not equal to 1");
              }
              return ServiceEndpoint.createCounterReport(
                  Counter4Utils.toJSON(r), reportType, provider, yearMonthsFromReport.get(0));
            })
        .collect(Collectors.toList());
  }

  @Override
  public Future<List<CounterReport>> fetchReport(
      String reportType, String beginDate, String endDate) {
    Context context = Vertx.currentContext();
    if (context == null) context = Vertx.vertx().getOrCreateContext();

    return context.executeBlocking(
        () -> {
          CounterReportResponse counterReportResponse;
          try {
            ReportRequest reportRequest = createReportRequest(reportType, beginDate, endDate);
            counterReportResponse = port.getReport(reportRequest);
          } catch (java.lang.Exception e) {
            String messages =
                ExceptionUtils.getThrowableList(e).stream()
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining(", "));
            throw new CS41Exception("Error getting report: " + messages);
          }

          List<Exception> exceptions = Counter4Utils.getExceptions(counterReportResponse);
          if (exceptions.isEmpty()
              && counterReportResponse.getReport() != null
              && !counterReportResponse.getReport().getReport().isEmpty()) {
            Report reportResult = counterReportResponse.getReport().getReport().get(0);
            try {
              return createCounterReportList(reportResult, reportType, provider);
            } catch (java.lang.Exception e) {
              throw new InvalidReportException(e);
            }
          } else {
            throw new InvalidReportException(Counter4Utils.getErrorMessages(exceptions));
          }
        },
        false);
  }

  static class CS41Exception extends RuntimeException {

    public CS41Exception(String message) {
      super(message);
    }
  }
}
