package org.olf.erm.usage.harvester.periodic;

import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.quartz.CronScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeriodicUtil {

  private static final Logger log = LoggerFactory.getLogger(PeriodicUtil.class);

  public static Trigger createTrigger(String tenantId, PeriodicConfig config) {
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
            CronScheduleBuilder.weeklyOnDayAndHourAndMinute(
                start.getDayOfWeek().getValue(), start.getHour(), start.getMinute());
        break;
      case MONTHLY:
        cronScheduleBuilder =
            CronScheduleBuilder.monthlyOnDayAndHourAndMinute(
                start.getDayOfMonth(), start.getHour(), start.getMinute());
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
