package org.olf.erm.usage.harvester.periodic;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulingUtil {

  private static final Logger log = LoggerFactory.getLogger(SchedulingUtil.class);

  public static void createOrUpdateJob(PeriodicConfig config, String tenantId) {
    try {
      createOrUpdateJob(StdSchedulerFactory.getDefaultScheduler(), config, tenantId);
    } catch (SchedulerException e) {
      log.error("Tenant: {}, unable to get default scheduler: {}", tenantId, e.getMessage());
    }
  }

  public static void createOrUpdateJob(
      Scheduler scheduler, PeriodicConfig config, String tenantId) {
    JobDetail job =
        JobBuilder.newJob()
            .ofType(HarvestTenantJob.class)
            .usingJobData("tenantId", tenantId)
            .withIdentity(new JobKey(tenantId))
            .build();

    Trigger trigger = createTrigger(tenantId, config);
    if (trigger != null) {
      try {
        if (scheduler.checkExists(new JobKey(tenantId))) {
          scheduler.rescheduleJob(new TriggerKey(tenantId), trigger);
          log.info(
              "Tenant: {}, Updated job trigger, next trigger: {}",
              tenantId,
              trigger.getNextFireTime());
        } else {
          scheduler.scheduleJob(job, trigger);
          log.info(
              "Tenant: {}, Scheduled new job, next trigger: {}",
              tenantId,
              trigger.getNextFireTime());
        }
      } catch (SchedulerException e) {
        log.error("Tenant: {}, Error scheduling job for tenant, {}", tenantId, e.getMessage());
      }
    } else {
      log.error("Tenant: {}, Error creating job trigger", tenantId);
    }
  }

  public static void deleteJob(Scheduler scheduler, String tenantId) {
    JobKey jobKey = new JobKey(tenantId);
    try {
      if (scheduler.checkExists(jobKey)) {
        scheduler.deleteJob(jobKey);
        log.info("Tenant: {}, removed job from schedule", tenantId);
      } else log.warn("Tenant: {}, no scheduled job found", tenantId);
    } catch (SchedulerException e) {
      log.error("Tenant: {}, error deleting job: {}", tenantId, e.getMessage());
    }
  }

  private static Trigger createTrigger(String tenantId, PeriodicConfig config) {
    LocalDateTime start =
        config.getStartAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    CronScheduleBuilder cronScheduleBuilder;
    switch (config.getPeriodicInterval()) {
      case DAILY:
        cronScheduleBuilder =
            CronScheduleBuilder.dailyAtHourAndMinute(start.getHour(), start.getMinute());
        break;
      case WEEKLY:
        cronScheduleBuilder =
            CronScheduleBuilder.cronSchedule(
                String.format(
                    "0 %d %d ? * %s",
                    start.getMinute(),
                    start.getHour(),
                    start.getDayOfWeek().toString().substring(0, 3)));
        break;
      case MONTHLY:
        if (start.getDayOfMonth() > 28) {
          cronScheduleBuilder =
              CronScheduleBuilder.cronSchedule(
                  String.format("0 %d %d L * ?", start.getMinute(), start.getHour()));
        } else {
          cronScheduleBuilder =
              CronScheduleBuilder.monthlyOnDayAndHourAndMinute(
                  start.getDayOfMonth(), start.getHour(), start.getMinute());
        }
        break;
      default:
        return null;
    }

    return TriggerBuilder.newTrigger()
        .startAt(Date.from(start.withSecond(0).atZone(ZoneId.systemDefault()).toInstant()))
        .withIdentity(new TriggerKey(tenantId))
        .withSchedule(cronScheduleBuilder.withMisfireHandlingInstructionFireAndProceed())
        .build();
  }
}
