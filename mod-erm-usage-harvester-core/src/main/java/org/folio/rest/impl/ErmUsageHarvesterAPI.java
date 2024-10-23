package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.POST;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.olf.erm.usage.harvester.client.ExtConfigurationsClientImpl.NO_ENTRY;
import static org.olf.erm.usage.harvester.periodic.JobInfoUtil.upsertJobInfo;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.PERIODIC_JOB_KEY;

import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.JobInfo;
import org.folio.rest.jaxrs.model.JobInfo.Result;
import org.folio.rest.jaxrs.model.JobInfos;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.olf.erm.usage.harvester.ClockProvider;
import org.olf.erm.usage.harvester.Messages;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClientImpl;
import org.olf.erm.usage.harvester.client.OkapiClientImpl;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  public static final String TABLE_NAME_JOBS = "jobs";
  public static final String STALE_JOB_ERROR_MSG = "Stale job";
  public static final String MESSAGE_NO_TOKEN = "No token provided";

  private static final Criteria finishedCriteria =
      new Criteria().addField("'finishedAt'").setJSONB(true).setOperation("IS NOT NULL");
  private static final Criteria notFinishedCritera =
      new Criteria().addField("'finishedAt'").setJSONB(true).setOperation("IS NULL");
  private static final Criteria notPeriodicJobTypeCriteria =
      new Criteria().setJSONB(true).addField("'type'").setOperation("!=").setVal(PERIODIC_JOB_KEY);
  private final UnaryOperator<HttpResponse<Buffer>> throwIfStatusCodeNot204 =
      resp -> {
        if (resp.statusCode() != 204) {
          throw new UnexpectedStatusCodeException(
              String.format(Messages.ERR_MSG_STATUS, resp.statusCode(), resp.statusMessage()));
        }
        return resp;
      };

  public static final String CONFIG_MODULE = "ERM-USAGE-HARVESTER";
  public static final String CONFIG_NAME = "daysToKeepLogs";
  public static final int DEFAULT_DAYS_TO_KEEP_LOGS = 60;

  private static final Logger log = LoggerFactory.getLogger(ErmUsageHarvesterAPI.class);

  private String createResponseEntity(Map<String, String> okapiHeaders) {
    return this.createResponseEntity(okapiHeaders, null);
  }

  private String createResponseEntity(Map<String, String> okapiHeaders, String providerId) {
    String message = String.format("Harvesting scheduled for tenant: %s", okapiHeaders.get(TENANT));
    if (providerId != null) message += ", providerId: " + providerId;
    return new JsonObject().put("message", message).toString();
  }

  @Override
  public void getErmUsageHarvesterStart(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    String token = okapiHeaders.get(XOkapiHeaders.TOKEN);
    if (token == null) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond500WithTextPlain(MESSAGE_NO_TOKEN)));
      return;
    }

    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      SchedulingUtil.scheduleTenantJob(scheduler, okapiHeaders.get(TENANT), token);
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond200WithApplicationJson(
                  createResponseEntity(okapiHeaders))));
    } catch (SchedulerException e) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getErmUsageHarvesterStartById(
      String id,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    String token = okapiHeaders.get(XOkapiHeaders.TOKEN);
    if (token == null) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond500WithTextPlain(MESSAGE_NO_TOKEN)));
    }

    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      SchedulingUtil.scheduleProviderJob(scheduler, okapiHeaders.get(TENANT), token, id);
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond200WithApplicationJson(
                  createResponseEntity(okapiHeaders, id))));
    } catch (SchedulerException e) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getErmUsageHarvesterImpl(
      String aggregator,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
      List<JsonObject> collect =
          ServiceEndpoint.getAvailableProviders().stream()
              .filter(
                  provider ->
                      Strings.isNullOrEmpty(aggregator)
                          || provider.isAggregator().equals(Boolean.valueOf(aggregator)))
              .sorted(Comparator.comparing(ServiceEndpointProvider::getServiceName))
              .map(ServiceEndpointProvider::toJson)
              .toList();
      String result = new JsonObject().put("implementations", new JsonArray(collect)).toString();
      asyncResultHandler.handle(
          succeededFuture(GetErmUsageHarvesterImplResponse.respond200WithApplicationJson(result)));
    } catch (Exception e) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterImplResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  private Criteria createTimestampCriteria(Number timestamp) {
    if (timestamp == null) {
      return new Criteria();
    }
    return new Criteria()
        .addField("'timestamp'")
        .setJSONB(true)
        .setOperation("<=")
        .setVal(String.valueOf(timestamp.longValue()));
  }

  private Criteria createProviderIdCriteria(String providerId) {
    if (Strings.isNullOrEmpty(providerId)) {
      return new Criteria();
    }
    return new Criteria()
        .addField("'providerId'")
        .setJSONB(true)
        .setOperation("=")
        .setVal(providerId);
  }

  @Override
  public void getErmUsageHarvesterJobs(
      Number timestamp,
      String providerId,
      String query,
      String totalRecords,
      int offset,
      int limit,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    CQL2PgJSON cql2PgJSON;
    try {
      cql2PgJSON = new CQL2PgJSON(TABLE_NAME_JOBS + ".jsonb");
    } catch (FieldException e) {
      asyncResultHandler.handle(
          succeededFuture(GetErmUsageHarvesterJobsResponse.respond500WithTextPlain(e)));
      return;
    }
    CQLWrapper cql =
        new CQLWrapper(cql2PgJSON, query, limit, offset)
            .addWrapper(new CQLWrapper(new Criterion(createTimestampCriteria(timestamp))))
            .addWrapper(new CQLWrapper(new Criterion(createProviderIdCriteria(providerId))));
    PgUtil.postgresClient(vertxContext, okapiHeaders)
        .get(TABLE_NAME_JOBS, JobInfo.class, cql, true)
        .map(
            res ->
                new JobInfos()
                    .withJobInfos(res.getResults())
                    .withTotalRecords(res.getResultInfo().getTotalRecords()))
        .<Response>map(GetErmUsageHarvesterJobsResponse::respond200WithApplicationJson)
        .otherwise(GetErmUsageHarvesterJobsResponse::respond500WithTextPlain)
        .onComplete(asyncResultHandler);
  }

  @Override
  public void postErmUsageHarvesterJobsPurgefinished(
      Number timestamp,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.postgresClient(vertxContext, okapiHeaders)
        .delete(
            TABLE_NAME_JOBS,
            new Criterion(createTimestampCriteria(timestamp)).addCriterion(finishedCriteria))
        .<Response>map(PostErmUsageHarvesterJobsPurgefinishedResponse.respond204())
        .otherwise(PostErmUsageHarvesterJobsPurgefinishedResponse::respond500WithTextPlain)
        .onComplete(asyncResultHandler);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void postErmUsageHarvesterJobsPurgestale(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    String tenantId = okapiHeaders.get(TENANT);
    long minus60Minutes = getCurrentTimestampMinus(60, ChronoUnit.MINUTES);
    PgUtil.postgresClient(vertxContext, okapiHeaders)
        .get(
            TABLE_NAME_JOBS,
            JobInfo.class,
            new Criterion()
                .addCriterion(createTimestampCriteria(minus60Minutes))
                .addCriterion(notPeriodicJobTypeCriteria)
                .addCriterion(notFinishedCritera))
        .flatMap(
            res -> {
              List<Future> upserts =
                  res.getResults().stream()
                      .<Future>map(
                          ji ->
                              upsertJobInfo(
                                  ji.withFinishedAt(
                                          Date.from(Instant.now(ClockProvider.getClock())))
                                      .withResult(Result.FAILURE)
                                      .withErrorMessage(STALE_JOB_ERROR_MSG),
                                  tenantId))
                      .toList();
              return CompositeFuture.join(upserts);
            })
        .<Response>map(cf -> PostErmUsageHarvesterJobsPurgestaleResponse.respond204())
        .otherwise(PostErmUsageHarvesterJobsPurgestaleResponse::respond500WithTextPlain)
        .onComplete(asyncResultHandler);
  }

  private long getCurrentTimestampMinus(long amountToSubstract, TemporalUnit unit) {
    return Instant.now(ClockProvider.getClock()).minus(amountToSubstract, unit).toEpochMilli();
  }

  @Override
  public void postErmUsageHarvesterJobsCleanup(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    String okapiUrl = vertxContext.config().getString("okapiUrl");
    String tenantId = okapiHeaders.get(TENANT);
    String token = okapiHeaders.get(TOKEN);
    OkapiClientImpl okapiClient = new OkapiClientImpl(okapiUrl);
    ExtConfigurationsClientImpl configurationsClient =
        new ExtConfigurationsClientImpl(okapiUrl, tenantId, token);

    okapiClient
        .sendRequest(POST, "/erm-usage-harvester/jobs/purgestale", tenantId, token)
        .map(throwIfStatusCodeNot204)
        .onFailure(t -> log.error("Error during cleanup: {}", t.toString()))
        .onComplete(
            ar ->
                configurationsClient
                    .getModConfigurationValue(CONFIG_MODULE, CONFIG_NAME)
                    .transform(
                        ar2 -> {
                          if (ar2.succeeded()) {
                            int days = Integer.parseInt(ar2.result());
                            if (days < 0) {
                              return failedFuture("Received invalid configuration value");
                            } else {
                              return succeededFuture(days);
                            }
                          } else {
                            if (NO_ENTRY.equals(ar2.cause().getMessage())) {
                              return succeededFuture(DEFAULT_DAYS_TO_KEEP_LOGS);
                            } else {
                              return failedFuture("Failed getting configuration value");
                            }
                          }
                        })
                    .map(i -> getCurrentTimestampMinus(i, ChronoUnit.DAYS))
                    .compose(
                        timestamp ->
                            okapiClient
                                .sendRequest(
                                    POST,
                                    "/erm-usage-harvester/jobs/purgefinished?timestamp="
                                        + timestamp,
                                    tenantId,
                                    token)
                                .map(throwIfStatusCodeNot204))
                    .onFailure(t -> log.error("Error during cleanup: {}", t.toString()))
                    .onComplete(
                        ar2 ->
                            asyncResultHandler.handle(
                                succeededFuture(
                                    PostErmUsageHarvesterJobsCleanupResponse.respond204()))));
  }

  static class UnexpectedStatusCodeException extends RuntimeException {

    public UnexpectedStatusCodeException(String message) {
      super(message);
    }
  }
}
