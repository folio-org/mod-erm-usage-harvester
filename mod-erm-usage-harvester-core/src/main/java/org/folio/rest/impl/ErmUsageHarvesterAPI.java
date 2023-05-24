package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.olf.erm.usage.harvester.WorkerVerticle.MESSAGE_NO_TOKEN;

import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.JobInfo;
import org.folio.rest.jaxrs.model.JobInfos;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  public static final String TABLE_NAME_JOBS = "jobs";

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
              .collect(Collectors.toList());
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
            new Criterion(createTimestampCriteria(timestamp))
                .addCriterion(
                    new Criteria()
                        .addField("'finishedAt'")
                        .setJSONB(true)
                        .setOperation("IS NOT NULL")))
        .<Response>map(PostErmUsageHarvesterJobsPurgefinishedResponse.respond204())
        .otherwise(PostErmUsageHarvesterJobsPurgefinishedResponse::respond500WithTextPlain)
        .onComplete(asyncResultHandler);
  }
}
