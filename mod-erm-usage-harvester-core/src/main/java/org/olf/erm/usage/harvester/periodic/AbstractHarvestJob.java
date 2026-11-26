package org.olf.erm.usage.harvester.periodic;

import java.util.List;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHarvestJob implements Job {

  static final String DATAKEY_TENANT = "tenantId";
  static final String DATAKEY_JOB_ID = "jobId";
  static final String DATAKEY_PROVIDER_ID = "providerId";
  static final String DATAKEY_TIMESTAMP = "timestamp";
  static final String DATAKEY_RETRY_ITEMS = "encodedRetryItems";

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHarvestJob.class);

  private String tenantId;
  private String providerId;
  private List<ParkedItem> retryItems;

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

  public void setEncodedRetryItems(String encodedRetryItems) {
    if (encodedRetryItems == null) {
      this.retryItems = null;
      return;
    }
    try {
      this.retryItems = RetryItemsCodec.decode(encodedRetryItems);
    } catch (IllegalArgumentException e) {
      LOGGER.error("Dropping retry items that can't be decoded: {}", encodedRetryItems, e);
      this.retryItems = List.of();
    }
  }

  /**
   * @return the retry items, or {@code null} for a regular run
   */
  public List<ParkedItem> getRetryItems() {
    return retryItems;
  }

  /**
   * @return {@code true} if it's a retry run
   */
  boolean isRetryRun() {
    return retryItems != null;
  }
}
