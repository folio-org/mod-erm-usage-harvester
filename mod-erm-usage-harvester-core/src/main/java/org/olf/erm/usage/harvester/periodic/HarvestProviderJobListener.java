package org.olf.erm.usage.harvester.periodic;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestProviderJobListener extends JobListenerSupport {
  private static final Logger log = LoggerFactory.getLogger(HarvestProviderJobListener.class);

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    if (context.getJobDetail().getJobClass().equals(HarvestProviderJob.class)
        && jobException != null) {
      log.error(jobException.getMessage());
    }
  }
}
