package org.olf.erm.usage.harvester.endpoints;

import io.reactivex.Observable;
import java.util.List;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;
import org.openapitools.client.model.SUSHIConsortiumMemberList;
import org.openapitools.client.model.SUSHIReportList;
import org.openapitools.client.model.SUSHIServiceStatus;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DefaultApi {

  @GET("status")
  Observable<List<SUSHIServiceStatus>> getAPIStatus(
      @Query("customer_id") String customerId, @Query("platform") String platform);

  @GET("members")
  Observable<List<SUSHIConsortiumMemberList>> getConsortiumMembers(
      @Query("customer_id") String customerId, @Query("platform") String platform);

  @GET("reports")
  Observable<List<SUSHIReportList>> getReports(
      @Query("customer_id") String customerId,
      @Query("platform") String platform,
      @Query("search") String search);

  @GET("reports/dr")
  Observable<COUNTERDatabaseReport> getReportsDR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/dr_d1")
  Observable<COUNTERDatabaseReport> getReportsDRD1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/dr_d2")
  Observable<COUNTERDatabaseReport> getReportsDRD2(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/ir")
  Observable<COUNTERItemReport> getReportsIR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/ir_a1")
  Observable<COUNTERItemReport> getReportsIRA1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/ir_m1")
  Observable<COUNTERItemReport> getReportsIRM1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/pr")
  Observable<COUNTERPlatformReport> getReportsPR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/pr_p1")
  Observable<COUNTERPlatformReport> getReportsPRP1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr")
  Observable<COUNTERTitleReport> getReportsTR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_b1")
  Observable<COUNTERTitleReport> getReportsTRB1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_b2")
  Observable<COUNTERTitleReport> getReportsTRB2(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_b3")
  Observable<COUNTERTitleReport> getReportsTRB3(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_j1")
  Observable<COUNTERTitleReport> getReportsTRJ1(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_j2")
  Observable<COUNTERTitleReport> getReportsTRJ2(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_j3")
  Observable<COUNTERTitleReport> getReportsTRJ3(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr_j4")
  Observable<COUNTERTitleReport> getReportsTRJ4(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);
}
