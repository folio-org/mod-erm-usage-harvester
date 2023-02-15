package org.olf.erm.usage.harvester.endpoints;

import io.reactivex.Observable;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DefaultApi {

  @GET("reports/dr?attributes_to_show=Data_Type|Access_Method")
  Observable<COUNTERDatabaseReport> getReportsDR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET(
      "reports/ir?attributes_to_show=Authors|Publication_Date|Article_Version|"
          + "Data_Type|YOP|Access_Type|Access_Method&Include_Parent_Details=True")
  Observable<COUNTERItemReport> getReportsIR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/pr?attributes_to_show=Data_Type|Access_Method")
  Observable<COUNTERPlatformReport> getReportsPR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);

  @GET("reports/tr?attributes_to_show=Data_Type|Section_Type|YOP|Access_Type|Access_Method")
  Observable<COUNTERTitleReport> getReportsTR(
      @Query("customer_id") String customerId,
      @Query("begin_date") String beginDate,
      @Query("end_date") String endDate,
      @Query("platform") String platform);
}
