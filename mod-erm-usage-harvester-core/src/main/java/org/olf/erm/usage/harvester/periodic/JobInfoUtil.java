package org.olf.erm.usage.harvester.periodic;

import static org.folio.rest.impl.ErmUsageHarvesterAPI.TABLE_NAME_JOBS;
import static org.folio.rest.jaxrs.model.JobInfo.Type.PERIODIC;
import static org.folio.rest.jaxrs.model.JobInfo.Type.PROVIDER;
import static org.folio.rest.jaxrs.model.JobInfo.Type.TENANT;
import static org.folio.rest.tools.utils.VertxUtils.getVertxFromContextOrNew;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_JOB_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TIMESTAMP;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.PERIODIC_JOB_KEY;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.TENANT_JOB_KEY;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.List;
import org.folio.rest.jaxrs.model.JobInfo;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.quartz.JobDetail;
import org.quartz.JobKey;

public class JobInfoUtil {

  private JobInfoUtil() {}

  public static JobInfo createJobInfo(JobDetail jobDetail) {
    JobInfo jobInfo = new JobInfo();
    JobKey jobKey = jobDetail.getKey();
    if (List.of(PERIODIC_JOB_KEY, TENANT_JOB_KEY).contains(jobKey.getName())) {
      jobInfo.setType(PERIODIC_JOB_KEY.equals(jobKey.getName()) ? PERIODIC : TENANT);
    } else {
      jobInfo.setType(PROVIDER);
      jobInfo.setProviderId(jobKey.getName());
    }
    jobInfo.setId(jobDetail.getJobDataMap().getString(DATAKEY_JOB_ID));
    jobInfo.setTimestamp(jobDetail.getJobDataMap().getLong(DATAKEY_TIMESTAMP));
    return jobInfo;
  }

  public static Future<String> upsertJobInfo(JobInfo jobInfo, String tenantId) {
    return PostgresClient.getInstance(getVertxFromContextOrNew(), tenantId)
        .upsert(TABLE_NAME_JOBS, jobInfo.getId(), jobInfo);
  }

  public static Future<RowSet<Row>> deletePeriodicJobInfo(String tenantId) {
    Criterion deleteCriterion =
        new Criterion()
            .addCriterion(
                new Criteria().addField("'type'").setOperation("=").setVal(PERIODIC.value()))
            .addCriterion(new Criteria().addField("'nextStart'").setOperation("!=").setVal("null"));
    return PostgresClient.getInstance(getVertxFromContextOrNew(), tenantId)
        .delete(TABLE_NAME_JOBS, deleteCriterion);
  }
}
