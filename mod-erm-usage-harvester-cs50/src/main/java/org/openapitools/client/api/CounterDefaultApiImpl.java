package org.openapitools.client.api;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiClient.AuthInfo;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;

public class CounterDefaultApiImpl extends DefaultApiImpl {

  private AuthInfo authInfo;

  public CounterDefaultApiImpl(ApiClient apiClient, AuthInfo authInfo) {
    super(apiClient);
    this.authInfo = authInfo;
  }

  public Future<COUNTERTitleReport> getReportsTR(
      String customerId, String beginDate, String endDate, String platform) {
    Promise<COUNTERTitleReport> promise = Promise.promise();
    super.getReportsTR(
        customerId,
        beginDate,
        endDate,
        platform,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "Data_Type|Section_Type|YOP|Access_Type|Access_Method",
        null,
        authInfo,
        promise);
    return promise.future();
  }

  public Future<COUNTERItemReport> getReportsIR(
      String customerId, String beginDate, String endDate, String platform) {
    Promise<COUNTERItemReport> promise = Promise.promise();
    super.getReportsIR(
        customerId,
        beginDate,
        endDate,
        platform,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "Authors|Publication_Date|Article_Version|Data_Type|YOP|Access_Type|Access_Method",
        null,
        "True",
        null,
        authInfo,
        promise);
    return promise.future();
  }

  public Future<COUNTERDatabaseReport> getReportsDR(
      String customerId, String beginDate, String endDate, String platform) {
    Promise<COUNTERDatabaseReport> promise = Promise.promise();
    super.getReportsDR(
        customerId,
        beginDate,
        endDate,
        platform,
        null,
        null,
        null,
        null,
        "Data_Type|Access_Method",
        null,
        authInfo,
        promise);
    return promise.future();
  }

  public Future<COUNTERPlatformReport> getReportsPR(
      String customerId, String beginDate, String endDate, String platform) {
    Promise<COUNTERPlatformReport> promise = Promise.promise();
    super.getReportsPR(
        customerId,
        beginDate,
        endDate,
        platform,
        null,
        null,
        null,
        "Data_Type|Access_Method",
        null,
        authInfo,
        promise);
    return promise.future();
  }
}
