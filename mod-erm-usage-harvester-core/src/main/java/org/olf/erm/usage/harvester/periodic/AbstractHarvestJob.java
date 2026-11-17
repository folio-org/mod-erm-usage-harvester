package org.olf.erm.usage.harvester.periodic;

import org.quartz.Job;

public abstract class AbstractHarvestJob implements Job {

  static final String DATAKEY_TENANT = "tenantId";
  static final String DATAKEY_JOB_ID = "jobId";
  static final String DATAKEY_PROVIDER_ID = "providerId";
  static final String DATAKEY_TIMESTAMP = "timestamp";

  private String tenantId;
  private String providerId;

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getProviderId() {
    return providerId;
  }
}
