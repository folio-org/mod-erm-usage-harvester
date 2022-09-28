package org.olf.erm.usage.harvester.periodic;

import org.quartz.Job;

public abstract class AbstractHarvestJob implements Job {

  public static final String DATAKEY_TENANT = "tenantId";
  public static final String DATAKEY_TOKEN = "token";
  public static final String DATAKEY_JOB_ID = "jobId";
  public static final String DATAKEY_PROVIDER_ID = "providerId";
  public static final String DATAKEY_TIMESTAMP = "timestamp";

  private String tenantId;
  private String token;
  private String providerId;

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getToken() {
    return token;
  }

  public String getProviderId() {
    return providerId;
  }
}
