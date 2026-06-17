package net.xdob.sample.smoke;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-platform runtime smoke runner for the cross-plugin JPA sample.
 */
public class RuntimeSmokeRunner {

  private final List<SmokeCheck> checks = new ArrayList<SmokeCheck>();
  private final long startedAt = System.currentTimeMillis();
  private Path runtime;
  private Path resultPath;
  private Path junitPath;
  private int port = 7791;
  private boolean keepWorkDir;
  private Process process;
  private Path smokeDir;
  private String failure;

  public static void main(String[] args) throws Exception {
    RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
    int exit = runner.run(args);
    if (exit != 0) {
      System.exit(exit);
    }
  }

  private int run(String[] args) throws Exception {
    parseArgs(args);
    String status = "FAILED";
    try {
      prepare();
      assertPluginZips();
      startHost("DISABLED");
      waitHostReady();
      checkJpaReloadDisabledNoMutation();
      stopHost();
      startHost("STOP_CONSUMERS_AND_REBUILD");
      waitHostReady();
      checkUnrelated();
      checkWorkflow();
      checkManagement();
      String reloadId = checkJpaReload();
      checkJpaReloadRecordPersistence(reloadId);
      checkJpaProviderReplacementPath();
      checkJpaReloadDrainTimeoutNoMutation();
      checkJpaProviderIsolation();
      checkActuator();
      addCheck("cleanup", "PASSED", "process cleanup requested");
      status = "PASSED";
      return 0;
    } catch (Exception e) {
      failure = e.getMessage();
      addCheck("runtimeSmoke", "FAILED", e.getMessage());
      System.out.println("SMOKE_FAILED " + e.getMessage());
      return 1;
    } finally {
      stopHost();
      writeReports(status);
      if (!keepWorkDir && "PASSED".equals(status)) {
        delete(smokeDir);
      }
    }
  }

  private void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--runtime".equals(arg)) {
        runtime = Paths.get(args[++i]);
      } else if ("--port".equals(arg)) {
        port = Integer.parseInt(args[++i]);
      } else if ("--result".equals(arg)) {
        resultPath = Paths.get(args[++i]);
      } else if ("--junit".equals(arg)) {
        junitPath = Paths.get(args[++i]);
      } else if ("--keep-work-dir".equals(arg)) {
        keepWorkDir = true;
      }
    }
    if (runtime == null) {
      throw new IllegalArgumentException("--runtime is required");
    }
  }

  private void prepare() throws Exception {
    runtime = runtime.toAbsolutePath().normalize();
    smokeDir = runtime.getParent().resolve("tmp").resolve("runtime-smoke-java").toAbsolutePath().normalize();
    delete(smokeDir);
    Files.createDirectories(smokeDir.resolve("logs"));
    Files.createDirectories(smokeDir.resolve("home"));
    Files.createDirectories(smokeDir.resolve("operations"));
    Files.createDirectories(smokeDir.resolve("jpa-reloads"));
  }

  private void assertPluginZips() throws Exception {
    File[] zips = runtime.resolve("plugins").toFile().listFiles((dir, name) -> name.endsWith(".zip"));
    int count = zips == null ? 0 : zips.length;
    System.out.println("SMOKE_PLUGIN_ZIPS count=" + count);
    if (count < 4) {
      addCheck("pluginZips", "FAILED", "Expected at least 4 plugin zips");
      throw new IllegalStateException("Expected at least 4 plugin zips");
    }
    addCheck("pluginZips", "PASSED", "count=" + count);
  }

  private void startHost(String jpaReloadMode) throws Exception {
    String java = Paths.get(System.getProperty("java.home"), "bin",
        isWindows() ? "java.exe" : "java").toString();
    List<String> command = new ArrayList<String>();
    command.add(java);
    command.add("-Duser.home=" + smokeDir.resolve("home"));
    command.add("-cp");
    command.add("lib/*");
    command.add("net.xdob.sample.host.CrossPluginJpaSampleHost");
    command.add("--spring.config.location=config/application.yml");
    command.add("--server.port=" + port);
    command.add("--spring.pf4boot.management.http.token=sample-token");
    command.add("--spring.pf4boot.management.http.operation-store.type=file");
    command.add("--spring.pf4boot.management.http.operation-store.directory=" + smokeDir.resolve("operations"));
    command.add("--spring.pf4boot.jpa.reload.mode=" + jpaReloadMode);
    command.add("--spring.pf4boot.jpa.reload.record-store.type=file");
    command.add("--spring.pf4boot.jpa.reload.record-store.directory=" + smokeDir.resolve("jpa-reloads"));
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(runtime.toFile());
    builder.redirectOutput(smokeDir.resolve("logs").resolve("stdout.log").toFile());
    builder.redirectError(smokeDir.resolve("logs").resolve("stderr.log").toFile());
    process = builder.start();
  }

  private void waitHostReady() throws Exception {
    long deadline = System.currentTimeMillis() + 120000L;
    while (System.currentTimeMillis() < deadline) {
      HttpResult result = request("GET", "/api/sample/workflow/summary", null, null);
      if (result.status == 200) {
        System.out.println("SMOKE_HOST_READY port=" + port);
        addCheck("hostReady", "PASSED", "HTTP 200");
        return;
      }
      Thread.sleep(1000L);
    }
    throw new IllegalStateException("PFS-001 host not ready within 120 seconds");
  }

  private void checkUnrelated() throws Exception {
    long deadline = System.currentTimeMillis() + 60000L;
    HttpResult unrelated = null;
    while (System.currentTimeMillis() < deadline) {
      unrelated = request("GET", "/api/sample/unrelated/health", null, null);
      if (unrelated.status == 200 && unrelated.body.contains("\"status\":\"UP\"")) {
        System.out.println("SMOKE_UNRELATED_PLUGIN_ALIVE status=" + unrelated.status);
        addCheck("unrelatedPluginAlive", "PASSED", "HTTP " + unrelated.status);
        return;
      }
      Thread.sleep(1000L);
    }
    String detail = unrelated == null ? "no response" : "HTTP " + unrelated.status + ": " + unrelated.body;
    throw new IllegalStateException("unrelated plugin did not report UP: " + detail);
  }

  private void checkWorkflow() throws Exception {
    String normalUser = "smoke-ok-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    HttpResult normal = request("GET",
        "/api/sample/workflow/place?username=" + normalUser + "&password=123&bookName=RuntimeBook",
        null,
        null);
    assertStatus(normal, 200, "workflow normal");
    System.out.println("SMOKE_WORKFLOW_OK status=" + normal.status);
    addCheck("workflowOk", "PASSED", "HTTP " + normal.status);

    HttpResult before = request("GET", "/api/sample/workflow/summary", null, null);
    assertStatus(before, 200, "summary before failure");
    long beforeUsers = numberField(before.body, "users");
    long beforeBooks = numberField(before.body, "books");
    long beforeAudits = numberField(before.body, "audits");
    String failUser = "smoke-fail-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    HttpResult failureResult = request("GET",
        "/api/sample/workflow/place?username=" + failUser
            + "&password=123&bookName=RollbackBook&failAfterAudit=true",
        null,
        null);
    assertStatus(failureResult, 500, "workflow forced failure");
    HttpResult after = request("GET", "/api/sample/workflow/summary", null, null);
    assertStatus(after, 200, "summary after failure");
    if (numberField(after.body, "users") != beforeUsers || numberField(after.body, "books") != beforeBooks) {
      throw new IllegalStateException("workflow rollback did not keep user/book counts stable");
    }
    if (numberField(after.body, "audits") < beforeAudits + 1) {
      throw new IllegalStateException("workflow failure did not keep REQUIRES_NEW audit evidence");
    }
    System.out.println("SMOKE_WORKFLOW_ROLLBACK status=" + failureResult.status);
    addCheck("workflowRollback", "PASSED", "HTTP " + failureResult.status);
    checkUnrelated();
  }

  private void checkManagement() throws Exception {
    List<Header> admin = new ArrayList<Header>();
    admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
    HttpResult plugins = request("GET", "/pf4boot/admin/plugins", admin, null);
    assertAdminSuccess(plugins, "management plugin list");

    List<Header> planHeaders = new ArrayList<Header>(admin);
    planHeaders.add(new Header("X-Idempotency-Key", "runtime-smoke-plan-java"));
    String body = "{\"pluginId\":\"sample-workflow\",\"stagedPluginPath\":\"plugin-workflow-3.0.0.zip\",\"dryRun\":true}";
    HttpResult plan = request("POST", "/pf4boot/admin/deployments/plan", planHeaders, body);
    assertAdminSuccess(plan, "management deployment plan");
    HttpResult replay = request("POST", "/pf4boot/admin/deployments/plan", planHeaders, body);
    assertAdminSuccess(replay, "management deployment plan replay");
    String firstOperation = stringField(plan.body, "operationId");
    String replayOperation = stringField(replay.body, "operationId");
    if (!firstOperation.equals(replayOperation)) {
      throw new IllegalStateException("idempotency replay did not return the original operation id");
    }
    System.out.println("SMOKE_MANAGEMENT_OPERATION operationId=" + firstOperation);
    System.out.println("SMOKE_IDEMPOTENCY_REPLAY operationId=" + replayOperation);
    addCheck("managementIdempotency", "PASSED", "operation replayed");
  }

  private void checkJpaProviderIsolation() throws Exception {
    List<Header> admin = new ArrayList<Header>();
    admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
    admin.add(new Header("X-Idempotency-Key", "runtime-smoke-stop-provider-java"));
    HttpResult stopped = request("POST", "/pf4boot/admin/plugins/sample-demo-jpa-domain/stop", admin, null);
    assertAdminSuccess(stopped, "management stop JPA provider");
    HttpResult unrelated = request("GET", "/api/sample/unrelated/health", null, null);
    assertStatus(unrelated, 200, "unrelated plugin after provider stop");
    if (!unrelated.body.contains("\"status\":\"UP\"")) {
      throw new IllegalStateException("unrelated plugin did not report UP after provider stop");
    }
    System.out.println("SMOKE_JPA_PROVIDER_ISOLATION status=" + unrelated.status);
    addCheck("jpaProviderIsolation", "PASSED", "unrelated plugin alive after provider stop");
  }

  private String checkJpaReload() throws Exception {
    List<Header> admin = new ArrayList<Header>();
    admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
    HttpResult plan = request("POST", "/pf4boot/admin/jpa/domains/demo/reload/plan", admin, "{}");
    assertAdminSuccess(plan, "JPA reload plan");
    if (!plan.body.contains("sample-demo-jpa-domain")
        || !plan.body.contains("sample-user-book-service")
        || !plan.body.contains("sample-workflow")) {
      throw new IllegalStateException("JPA reload plan did not include provider and consumers: " + plan.body);
    }
    System.out.println("SMOKE_JPA_RELOAD_PLAN status=" + plan.status);
    addCheck("jpaReloadPlanOnly", "PASSED", "plan generated");

    List<Header> reloadHeaders = new ArrayList<Header>(admin);
    reloadHeaders.add(new Header("X-Idempotency-Key", "runtime-smoke-jpa-reload-java"));
    HttpResult reload = request("POST", "/pf4boot/admin/jpa/domains/demo/reload", reloadHeaders, "{}");
    assertAdminSuccess(reload, "JPA reload execute");
    String reloadId = stringField(reload.body, "reloadId");
    HttpResult replay = request("POST", "/pf4boot/admin/jpa/domains/demo/reload", reloadHeaders, "{}");
    assertAdminSuccess(replay, "JPA reload replay");
    String replayReloadId = stringField(replay.body, "reloadId");
    if (!reloadId.equals(replayReloadId)) {
      throw new IllegalStateException("JPA reload idempotency did not replay reloadId");
    }
    System.out.println("SMOKE_JPA_RELOAD_SUCCESS reloadId=" + reloadId);
    System.out.println("SMOKE_JPA_RELOAD_IDEMPOTENCY reloadId=" + replayReloadId);
    addCheck("jpaReloadSuccess", "PASSED", reloadId);
    addCheck("jpaReloadIdempotency", "PASSED", replayReloadId);
    HttpResult record = request("GET", "/pf4boot/admin/jpa/reloads/" + reloadId, admin, null);
    assertAdminSuccess(record, "JPA reload record");
    if (!record.body.contains("\"drainReport\"") || !record.body.contains("\"accepted\":true")) {
      throw new IllegalStateException("JPA reload record did not include accepted drain report: " + record.body);
    }
    System.out.println("SMOKE_JPA_RELOAD_DRAIN_SUCCESS reloadId=" + reloadId);
    addCheck("jpaReloadRecord", "PASSED", reloadId);
    addCheck("jpaReloadDrainSuccess", "PASSED", reloadId);

    HttpResult summary = request("GET", "/api/sample/workflow/summary", null, null);
    assertStatus(summary, 200, "workflow summary after JPA reload");
    return reloadId;
  }

  private void checkJpaReloadRecordPersistence(String reloadId) throws Exception {
    stopHost();
    startHost("STOP_CONSUMERS_AND_REBUILD");
    waitHostReady();
    HttpResult jpaReload = request("GET", "/actuator/pf4bootjpareload", null, null);
    assertStatus(jpaReload, 200, "actuator JPA reload after restart");
    if (!jpaReload.body.contains(reloadId)
        || !jpaReload.body.contains("FileJpaDomainReloadRecordRepository")
        || !jpaReload.body.contains("recentRecordCount")) {
      throw new IllegalStateException("JPA reload persisted record was not visible after restart: " + jpaReload.body);
    }
    System.out.println("SMOKE_JPA_RELOAD_RECORD_PERSISTED reloadId=" + reloadId);
    addCheck("jpaReloadRecordPersistence", "PASSED", reloadId);
  }

  private void checkJpaProviderReplacementPath() throws Exception {
    List<Header> admin = new ArrayList<Header>();
    admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
    admin.add(new Header("X-Idempotency-Key", "runtime-smoke-jpa-provider-replace-java"));
    HttpResult reload = request("POST", "/pf4boot/admin/jpa/domains/demo/reload", admin,
        "{\"providerReplacementPath\":\"plugin-demo-jpa-domain-3.0.0.zip\"}");
    assertAdminSuccess(reload, "JPA provider replacement reload");
    if (!reload.body.contains("\"providerReplacementSummary\"")
        || !reload.body.contains("\"state\":\"SUCCEEDED\"")
        || !reload.body.contains("\"targetPluginId\":\"sample-demo-jpa-domain\"")) {
      throw new IllegalStateException("JPA provider replacement summary missing: " + reload.body);
    }
    HttpResult summary = request("GET", "/api/sample/workflow/summary", null, null);
    assertStatus(summary, 200, "workflow summary after JPA provider replacement");
    System.out.println("SMOKE_JPA_PROVIDER_REPLACEMENT_PATH status=" + reload.status);
    addCheck("jpaProviderReplacementPath", "PASSED", "sample-demo-jpa-domain");
  }

  private void checkJpaReloadDrainTimeoutNoMutation() throws Exception {
    Path marker = smokeDir.resolve("home").resolve("jpa-drain-timeout.flag");
    Files.createFile(marker);
    try {
      List<Header> admin = new ArrayList<Header>();
      admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
      admin.add(new Header("X-Idempotency-Key", "runtime-smoke-jpa-drain-timeout-java"));
      HttpResult reload = request("POST", "/pf4boot/admin/jpa/domains/demo/reload", admin,
          "{\"drainTimeoutMillis\":10}");
      assertAdminSuccess(reload, "JPA reload drain timeout response");
      if (!reload.body.contains("DRAIN_TIMEOUT")) {
        throw new IllegalStateException("JPA reload drain timeout did not report DRAIN_TIMEOUT: " + reload.body);
      }
      HttpResult workflow = request("GET", "/api/sample/workflow/summary", null, null);
      assertStatus(workflow, 200, "workflow after JPA drain timeout");
      HttpResult unrelated = request("GET", "/api/sample/unrelated/health", null, null);
      assertStatus(unrelated, 200, "unrelated after JPA drain timeout");
      System.out.println("SMOKE_JPA_RELOAD_DRAIN_TIMEOUT_NO_MUTATION status=" + reload.status);
      addCheck("jpaReloadDrainTimeoutNoMutation", "PASSED", "DRAIN_TIMEOUT");
    } finally {
      Files.deleteIfExists(marker);
    }
  }

  private void checkJpaReloadDisabledNoMutation() throws Exception {
    List<Header> admin = new ArrayList<Header>();
    admin.add(new Header("X-PF4Boot-Admin-Token", "sample-token"));
    admin.add(new Header("X-Idempotency-Key", "runtime-smoke-jpa-disabled-java"));
    HttpResult reload = request("POST", "/pf4boot/admin/jpa/domains/demo/reload", admin, "{}");
    assertStatus(reload, 200, "JPA reload disabled response");
    if (!reload.body.contains("RELOAD_DISABLED")) {
      throw new IllegalStateException("disabled JPA reload did not report RELOAD_DISABLED: " + reload.body);
    }
    HttpResult unrelated = request("GET", "/api/sample/unrelated/health", null, null);
    assertStatus(unrelated, 200, "unrelated plugin after disabled JPA reload");
    System.out.println("SMOKE_JPA_RELOAD_DISABLED_NO_MUTATION status=" + reload.status);
    addCheck("jpaReloadDisabledNoMutation", "PASSED", "RELOAD_DISABLED");
  }

  private void checkActuator() throws Exception {
    HttpResult governance = request("GET", "/actuator/pf4bootgovernance", null, null);
    assertStatus(governance, 200, "actuator governance");
    HttpResult jpaReload = request("GET", "/actuator/pf4bootjpareload", null, null);
    assertStatus(jpaReload, 200, "actuator JPA reload");
    if (!jpaReload.body.contains("lastDrainFailureCode")
        || !jpaReload.body.contains("DRAIN_TIMEOUT")
        || !jpaReload.body.contains("lastDrainPluginCount")
        || !jpaReload.body.contains("FileJpaDomainReloadRecordRepository")) {
      throw new IllegalStateException("actuator JPA reload did not expose drain summary: " + jpaReload.body);
    }
    HttpResult metrics = request("GET", "/actuator/metrics/pf4boot.management.request.total", null, null);
    assertStatus(metrics, 200, "actuator management metric");
    System.out.println("SMOKE_ACTUATOR_GOVERNANCE status=" + governance.status);
    System.out.println("SMOKE_ACTUATOR_JPA_RELOAD status=" + jpaReload.status);
    addCheck("actuatorGovernance", "PASSED", "HTTP " + governance.status);
    addCheck("actuatorJpaReload", "PASSED", "HTTP " + jpaReload.status);
    addCheck("actuatorJpaReloadDrainSummary", "PASSED", "DRAIN_TIMEOUT/file-store");
  }

  private HttpResult request(String method, String path, List<Header> headers, String body) throws Exception {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(20000);
      if (headers != null) {
        for (Header header : headers) {
          connection.setRequestProperty(header.name, header.value);
        }
      }
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
      }
      int status = connection.getResponseCode();
      BufferedReader reader = new BufferedReader(new InputStreamReader(
          status >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line);
      }
      return new HttpResult(status, content.toString());
    } catch (java.io.IOException e) {
      return new HttpResult(0, e.getMessage());
    }
  }

  private void assertStatus(HttpResult result, int expected, String label) {
    if (result.status != expected) {
      throw new IllegalStateException(label + " expected HTTP " + expected + " but got " + result.status
          + ": " + result.body);
    }
  }

  private void assertAdminSuccess(HttpResult result, String label) {
    assertStatus(result, 200, label);
    if (!result.body.contains("\"success\":true")) {
      throw new IllegalStateException(label + " expected management success response: " + result.body);
    }
  }

  private long numberField(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9]+)").matcher(json);
    if (!matcher.find()) {
      throw new IllegalStateException("JSON number field not found: " + field);
    }
    return Long.parseLong(matcher.group(1));
  }

  private String stringField(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    if (!matcher.find()) {
      throw new IllegalStateException("JSON string field not found: " + field);
    }
    return matcher.group(1);
  }

  private void addCheck(String name, String status, String message) {
    checks.add(new SmokeCheck(name, status, message));
  }

  private void writeReports(String status) throws Exception {
    if (resultPath != null) {
      Files.createDirectories(resultPath.toAbsolutePath().getParent());
      FileWriter writer = new FileWriter(resultPath.toFile());
      try {
        writer.write("{\"status\":\"" + status + "\",\"startedAt\":" + startedAt
            + ",\"finishedAt\":" + System.currentTimeMillis()
            + ",\"port\":" + port
            + ",\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
          if (i > 0) {
            writer.write(",");
          }
          SmokeCheck check = checks.get(i);
          writer.write("{\"name\":\"" + escape(check.name) + "\",\"status\":\"" + check.status
              + "\",\"message\":\"" + escape(check.message) + "\"}");
        }
        writer.write("],\"failure\":");
        writer.write(failure == null ? "null" : "\"" + escape(failure) + "\"");
        writer.write("}");
      } finally {
        writer.close();
      }
    }
    if (junitPath != null) {
      Files.createDirectories(junitPath.toAbsolutePath().getParent());
      int failures = 0;
      for (SmokeCheck check : checks) {
        if (!"PASSED".equals(check.status)) {
          failures++;
        }
      }
      FileWriter writer = new FileWriter(junitPath.toFile());
      try {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<testsuite name=\"RuntimeSmoke\" tests=\"" + checks.size() + "\" failures=\"" + failures + "\">\n");
        for (SmokeCheck check : checks) {
          writer.write("  <testcase classname=\"RuntimeSmoke\" name=\"" + escapeXml(check.name) + "\">");
          if (!"PASSED".equals(check.status)) {
            writer.write("<failure message=\"" + escapeXml(check.message) + "\"/>");
          }
          writer.write("</testcase>\n");
        }
        writer.write("</testsuite>\n");
      } finally {
        writer.close();
      }
    }
  }

  private void stopHost() {
    if (process != null) {
      process.destroy();
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private void delete(Path path) throws Exception {
    if (path == null || !Files.exists(path)) {
      return;
    }
    Files.walk(path)
        .sorted((left, right) -> right.compareTo(left))
        .forEach(item -> {
          try {
            Files.deleteIfExists(item);
          } catch (Exception ignored) {
          }
        });
  }

  private String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\r", " ").replace("\n", " ");
  }

  private String escapeXml(String value) {
    return escape(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private static class Header {
    private final String name;
    private final String value;

    Header(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }

  private static class HttpResult {
    private final int status;
    private final String body;

    HttpResult(int status, String body) {
      this.status = status;
      this.body = body == null ? "" : body;
    }
  }

  private static class SmokeCheck {
    private final String name;
    private final String status;
    private final String message;

    SmokeCheck(String name, String status, String message) {
      this.name = name;
      this.status = status;
      this.message = message;
    }
  }
}
