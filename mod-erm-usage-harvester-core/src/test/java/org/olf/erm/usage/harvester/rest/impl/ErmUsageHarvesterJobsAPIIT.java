package org.olf.erm.usage.harvester.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.ErmUsageHarvesterAPI.TABLE_NAME_JOBS;

import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.JobInfo;
import org.folio.rest.jaxrs.model.JobInfos;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.PostgresContainerRule;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterJobsAPIIT {

  private static final String TENANT = "tenant1";
  private static final String TOKEN = "some_token";
  private static final String PARAM_OFFSET = "offset";
  private static final String PARAM_LIMIT = "limit";
  private static final String PARAM_QUERY = "query";
  private static final String PARAM_PROVIDER_ID = "providerId";
  private static final String PARAM_TIMESTAMP = "timestamp";
  private static final Map<String, String> OKAPI_HEADERS =
      Map.of(XOkapiHeaders.TENANT, TENANT, XOkapiHeaders.TOKEN, TOKEN);

  private static final Vertx vertx = Vertx.vertx();
  private static JobInfos expectedJobInfos;

  @ClassRule
  public static PostgresContainerRule postgresContainerRule =
      new PostgresContainerRule(vertx, TENANT);

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    int port = NetworkUtils.nextFreePort();
    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester/jobs";
    RestAssured.requestSpecification = new RequestSpecBuilder().addHeaders(OKAPI_HEADERS).build();
    RestAssured.defaultParser = Parser.JSON;

    String expectedJobInfosStr =
        Resources.toString(Resources.getResource("sample-jobs.json"), StandardCharsets.UTF_8);
    expectedJobInfos = Json.decodeValue(expectedJobInfosStr, JobInfos.class);

    JsonObject cfg = new JsonObject().put("http.port", port).put("testing", true);
    vertx
        .deployVerticle("org.folio.rest.RestVerticle", new DeploymentOptions().setConfig(cfg))
        .compose(s -> populateDb(expectedJobInfos))
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    RestAssured.reset();
  }

  private static Future<RowSet<Row>> populateDb(JobInfos jobInfos) {
    return PostgresClient.getInstance(vertx, TENANT)
        .saveBatch(TABLE_NAME_JOBS, jobInfos.getJobInfos());
  }

  private static Future<RowSet<Row>> clearDb() {
    return PostgresClient.getInstance(vertx, TENANT)
        .delete(TABLE_NAME_JOBS, new Criterion(new Criteria()));
  }

  private Consumer<JobInfos> hasResultSizes(int size, int totalRecords) {
    return jobInfos -> {
      assertThat(jobInfos.getJobInfos()).hasSize(size);
      assertThat(jobInfos.getTotalRecords()).isEqualTo(totalRecords);
    };
  }

  private Consumer<JobInfos> containsIds(String... ids) {
    return jobInfos ->
        assertThat(jobInfos.getJobInfos())
            .extracting(JobInfo::getId)
            .containsExactlyInAnyOrder(ids);
  }

  @Test
  public void testReturnedData() {
    JobInfos getAllJobInfos = new GetJobsRequest().withParams(PARAM_LIMIT, 1000).send();
    assertThat(getAllJobInfos).usingRecursiveComparison().isEqualTo(expectedJobInfos);
  }

  @Test
  public void testPaging() {
    JobInfos getPage1 = new GetJobsRequest().send();
    assertThat(getPage1).satisfies(hasResultSizes(10, expectedJobInfos.getTotalRecords()));

    JobInfos getPage2 = new GetJobsRequest().withParams(PARAM_OFFSET, 10).send();
    assertThat(getPage2).satisfies(hasResultSizes(8, expectedJobInfos.getTotalRecords()));
    assertThat(getPage1.getJobInfos())
        .usingRecursiveFieldByFieldElementComparator()
        .doesNotContainAnyElementsOf(getPage2.getJobInfos());
  }

  @Test
  public void testQueryParam() {
    JobInfos result = new GetJobsRequest().withParams(PARAM_QUERY, "type==tenant").send();
    assertThat(result)
        .satisfies(hasResultSizes(1, 1), containsIds("2db51f86-6902-4120-98a4-d43c7cb0cb98"));
  }

  @Test
  public void testTimestampParam() {
    JobInfos result = new GetJobsRequest().withParams(PARAM_TIMESTAMP, "1663150477573").send();
    assertThat(result)
        .satisfies(
            hasResultSizes(2, 2),
            containsIds(
                "1e8383b6-7690-4f31-a237-11a130c697b6", "2db51f86-6902-4120-98a4-d43c7cb0cb98"));
  }

  @Test
  public void testProviderIdParam() {
    JobInfos result =
        new GetJobsRequest()
            .withParams(PARAM_PROVIDER_ID, "35f68a61-b12c-4f14-a3b7-8518a0ef42fa")
            .send();
    assertThat(result)
        .satisfies(hasResultSizes(1, 1), containsIds("fc3e5bb1-d13e-4f4c-9ea2-b31da39c58bb"));
  }

  @Test
  public void testPurgeFinishedJobs() throws ExecutionException, InterruptedException {
    try {
      assertThat(new GetJobsRequest().send()).satisfies(hasResultSizes(10, 18));

      // purge timestamp
      given().queryParam("timestamp", 1663150479004L).post("/purgefinished").then().statusCode(204);
      assertThat(new GetJobsRequest().send())
          .satisfies(
              hasResultSizes(4, 4),
              containsIds(
                  "42ddd915-a046-4613-8272-e25b0edf36a1",
                  "2f4dd174-ea99-45b0-9fe7-3332764c9ee5",
                  "fcfd4c17-1ba4-41b1-b3b0-45ff3002dff4",
                  "910c8c0e-e331-4800-935e-e13232e30190"));

      // purge all finished jobs
      given().post("/purgefinished").then().statusCode(204);
      assertThat(new GetJobsRequest().send())
          .satisfies(hasResultSizes(1, 1), containsIds("42ddd915-a046-4613-8272-e25b0edf36a1"));
    } finally {
      clearDb()
          .compose(rs -> populateDb(expectedJobInfos))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
    }
  }

  static class GetJobsRequest {
    RequestSpecification reqSpec = given();

    public GetJobsRequest withParams(
        String firstParameterName, Object firstParameterValue, Object... parameterNameValuePairs) {
      reqSpec.params(firstParameterName, firstParameterValue, parameterNameValuePairs);
      return this;
    }

    public JobInfos send() {
      return reqSpec.get().then().statusCode(200).extract().as(JobInfos.class);
    }
  }
}
