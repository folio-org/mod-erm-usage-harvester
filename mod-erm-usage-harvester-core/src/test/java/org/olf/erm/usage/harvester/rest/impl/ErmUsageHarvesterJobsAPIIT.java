package org.olf.erm.usage.harvester.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.ErmUsageHarvesterAPI.STALE_JOB_ERROR_MSG;
import static org.folio.rest.impl.ErmUsageHarvesterAPI.TABLE_NAME_JOBS;
import static org.folio.rest.jaxrs.model.JobInfo.Result.FAILURE;
import static org.olf.erm.usage.harvester.Constants.DEFAULT_DAYS_TO_KEEP_LOGS;
import static org.olf.erm.usage.harvester.Constants.SETTINGS_KEY_DAYS_TO_KEEP_LOGS;
import static org.olf.erm.usage.harvester.Constants.SETTINGS_SCOPE_HARVESTER;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.JobInfo;
import org.folio.rest.jaxrs.model.JobInfos;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.settings.Entries;
import org.folio.settings.Entry;
import org.folio.settings.ResultInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.ClockProvider;
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
  private static final String BASE_PATH = "/erm-usage-harvester/jobs";
  private static final String PURGE_PATH_SEGMENT = "/purgefinished";
  private static final String PURGE_PATH = BASE_PATH + PURGE_PATH_SEGMENT;
  private static final String PURGE_STALE_PATH = BASE_PATH + "/purgestale";
  private static final String SETTINGS_PATH = "/settings/entries";
  private static final String CLEANUP_PATH_SEGMENT = "/cleanup";

  private static JobInfos expectedJobInfos;
  private static JobInfos expectedStaleJobInfos;

  @ClassRule
  public static PostgresContainerRule postgresContainerRule =
      new PostgresContainerRule(vertx, TENANT);

  @ClassRule
  public static WireMockRule okapiMockRule =
      new WireMockRule(new WireMockConfiguration().dynamicPort());

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    int port = NetworkUtils.nextFreePort();
    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = BASE_PATH;
    RestAssured.requestSpecification = new RequestSpecBuilder().addHeaders(OKAPI_HEADERS).build();
    RestAssured.defaultParser = Parser.JSON;

    String expectedJobInfosStr =
        Resources.toString(Resources.getResource("sample-jobs.json"), StandardCharsets.UTF_8);
    expectedJobInfos = Json.decodeValue(expectedJobInfosStr, JobInfos.class);
    String expectedStaleJobInfosStr =
        Resources.toString(Resources.getResource("sample-jobs-stale.json"), StandardCharsets.UTF_8);
    expectedStaleJobInfos = Json.decodeValue(expectedStaleJobInfosStr, JobInfos.class);

    JsonObject cfg =
        new JsonObject()
            .put("okapiUrl", okapiMockRule.baseUrl())
            .put("http.port", port)
            .put("testing", true);
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
  public void testPurgeStaleJobs() throws ExecutionException, InterruptedException {
    try {
      // set up test data
      clearDb()
          .compose(rs -> populateDb(expectedStaleJobInfos))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
      assertThat(new GetJobsRequest().send())
          .usingRecursiveComparison()
          .isEqualTo(expectedStaleJobInfos);

      // stale jobs are not older than 60 minutes
      ClockProvider.setClock(
          Clock.fixed(LocalDateTime.of(2023, 1, 2, 8, 30, 0).toInstant(UTC), UTC));
      given().post("/purgestale").then().statusCode(204);
      assertThat(new GetJobsRequest().send())
          .usingRecursiveComparison()
          .isEqualTo(expectedStaleJobInfos);

      // stale jobs are older than 60 minutes
      Instant testInstant = LocalDateTime.of(2023, 1, 2, 9, 5, 0).toInstant(UTC);
      ClockProvider.setClock(Clock.fixed(testInstant, UTC));
      given().post("/purgestale").then().statusCode(204);

      List<String> idsExpectedToChange =
          List.of(
              "055ca7f2-6156-450c-9e39-a89f05e3544f",
              "9127e10b-3603-443b-8a40-cbe5e2e1bb19",
              "b39c0e8d-9ff1-41fc-8851-c49f61b085e1");
      Map<Boolean, List<JobInfo>> jobsExpectedToChange =
          new GetJobsRequest()
              .send().getJobInfos().stream()
                  .collect(Collectors.groupingBy(ji -> idsExpectedToChange.contains(ji.getId())));
      assertThat(expectedStaleJobInfos.getJobInfos())
          .usingRecursiveFieldByFieldElementComparator()
          .containsAll(jobsExpectedToChange.get(false));
      assertThat(jobsExpectedToChange.get(true))
          .allSatisfy(
              ji -> {
                assertThat(ji.getErrorMessage()).isEqualTo(STALE_JOB_ERROR_MSG);
                assertThat(ji.getResult()).isEqualTo(FAILURE);
                assertThat(ji.getFinishedAt()).isEqualTo(Date.from(testInstant));
              });

    } finally {
      clearDb()
          .compose(rs -> populateDb(expectedJobInfos))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
    }
  }

  @Test
  public void testPurgeFinishedJobs() throws ExecutionException, InterruptedException {
    try {
      assertThat(new GetJobsRequest().send()).satisfies(hasResultSizes(10, 18));

      // purge timestamp
      given()
          .queryParam(PARAM_TIMESTAMP, 1663150479004L)
          .post(PURGE_PATH_SEGMENT)
          .then()
          .statusCode(204);
      assertThat(new GetJobsRequest().send())
          .satisfies(
              hasResultSizes(4, 4),
              containsIds(
                  "42ddd915-a046-4613-8272-e25b0edf36a1",
                  "2f4dd174-ea99-45b0-9fe7-3332764c9ee5",
                  "fcfd4c17-1ba4-41b1-b3b0-45ff3002dff4",
                  "910c8c0e-e331-4800-935e-e13232e30190"));

      // purge all finished jobs
      given().post(PURGE_PATH_SEGMENT).then().statusCode(204);
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

  private Entries createSettingsResponse(Object value) {
    return new Entries()
        .withItems(
            List.of(
                new Entry()
                    .withScope(SETTINGS_SCOPE_HARVESTER)
                    .withKey(SETTINGS_KEY_DAYS_TO_KEEP_LOGS)
                    .withValue(value)))
        .withResultInfo(new ResultInfo().withTotalRecords(1));
  }

  @Test
  public void testCleanup() {
    okapiMockRule.stubFor(post(urlPathEqualTo(PURGE_PATH)).willReturn(noContent()));
    okapiMockRule.stubFor(post(urlPathEqualTo(PURGE_STALE_PATH)).willReturn(noContent()));

    // failed to get settings value
    okapiMockRule.stubFor(WireMock.get(urlPathEqualTo(SETTINGS_PATH)).willReturn(serverError()));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    verify(0, postRequestedFor(urlPathEqualTo(PURGE_PATH)));

    // settings value is set to null
    okapiMockRule.resetRequests();
    okapiMockRule.stubFor(
        WireMock.get(urlPathEqualTo(SETTINGS_PATH))
            .willReturn(okJson(Json.encode(createSettingsResponse(null)))));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_PATH)));

    // settings value is set to invalid value
    okapiMockRule.resetRequests();
    okapiMockRule.stubFor(
        WireMock.get(urlPathEqualTo(SETTINGS_PATH))
            .willReturn(okJson(Json.encode(createSettingsResponse(-10)))));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    verify(0, postRequestedFor(urlPathEqualTo(PURGE_PATH)));

    // set fixed clock
    LocalDateTime testDateTime = LocalDateTime.of(2000, 1, 30, 8, 5, 3, 123);
    ClockProvider.setClock(Clock.fixed(testDateTime.toInstant(UTC), UTC));

    // no settings entry is found
    okapiMockRule.resetRequests();
    okapiMockRule.stubFor(
        WireMock.get(urlPathEqualTo(SETTINGS_PATH))
            .willReturn(
                okJson(
                    Json.encode(
                        new Entries()
                            .withItems(emptyList())
                            .withResultInfo(new ResultInfo().withTotalRecords(0))))));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    long expectedTimestamp =
        testDateTime
            .minus(DEFAULT_DAYS_TO_KEEP_LOGS, ChronoUnit.DAYS)
            .toInstant(UTC)
            .toEpochMilli();
    verify(
        1,
        postRequestedFor(urlPathEqualTo(PURGE_PATH))
            .withQueryParam(PARAM_TIMESTAMP, equalTo(String.valueOf(expectedTimestamp)))
            .withHeader(XOkapiHeaders.TENANT, equalTo(TENANT))
            .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN)));

    // settings value is set to 10
    okapiMockRule.resetRequests();
    okapiMockRule.stubFor(
        WireMock.get(urlPathEqualTo(SETTINGS_PATH))
            .willReturn(okJson(Json.encode(createSettingsResponse(10)))));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    expectedTimestamp = testDateTime.minus(10, ChronoUnit.DAYS).toInstant(UTC).toEpochMilli();
    verify(
        1,
        postRequestedFor(urlPathEqualTo(PURGE_PATH))
            .withQueryParam(PARAM_TIMESTAMP, equalTo(String.valueOf(expectedTimestamp)))
            .withHeader(XOkapiHeaders.TENANT, equalTo(TENANT))
            .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN)));

    // purge endpoints return status code 500
    okapiMockRule.resetRequests();
    okapiMockRule.stubFor(post(urlPathEqualTo(PURGE_PATH)).willReturn(serverError()));
    okapiMockRule.stubFor(post(urlPathEqualTo(PURGE_STALE_PATH)).willReturn(serverError()));
    given().post(CLEANUP_PATH_SEGMENT).then().statusCode(204);
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_STALE_PATH)));
    verify(1, getRequestedFor(urlPathEqualTo(SETTINGS_PATH)));
    verify(1, postRequestedFor(urlPathEqualTo(PURGE_PATH)));
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
