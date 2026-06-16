package net.xdob.sample.saga.smoke;

import java.io.BufferedReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saga/Outbox 示例 smoke runner。
 */
public class SagaOutboxSmokeRunner {

  private final List<String> checks = new ArrayList<String>();
  private Path runtime;
  private Path resultPath;
  private Path smokeDir;
  private int port = 7792;
  private Process process;

  public static void main(String[] args) throws Exception {
    int exit = new SagaOutboxSmokeRunner().run(args);
    if (exit != 0) {
      System.exit(exit);
    }
  }

  private int run(String[] args) throws Exception {
    parseArgs(args);
    String status = "FAILED";
    try {
      prepare();
      startHost();
      waitReady();
      checkNormalOrder();
      checkRetryOrder();
      status = "PASSED";
      return 0;
    } finally {
      stopHost();
      writeReport(status);
    }
  }

  private void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if ("--runtime".equals(args[i])) {
        runtime = Paths.get(args[++i]);
      } else if ("--port".equals(args[i])) {
        port = Integer.parseInt(args[++i]);
      } else if ("--result".equals(args[i])) {
        resultPath = Paths.get(args[++i]);
      }
    }
    if (runtime == null) {
      throw new IllegalArgumentException("--runtime is required");
    }
  }

  private void prepare() throws Exception {
    runtime = runtime.toAbsolutePath().normalize();
    smokeDir = runtime.getParent().resolve("tmp").resolve("runtime-smoke").toAbsolutePath().normalize();
    Files.createDirectories(smokeDir.resolve("home"));
    Files.createDirectories(smokeDir.resolve("logs"));
  }

  private void startHost() throws Exception {
    String java = Paths.get(System.getProperty("java.home"), "bin",
        isWindows() ? "java.exe" : "java").toString();
    List<String> command = new ArrayList<String>();
    command.add(java);
    command.add("-Duser.home=" + smokeDir.resolve("home"));
    command.add("-cp");
    command.add("lib/*");
    command.add("net.xdob.sample.saga.SagaOutboxSampleHost");
    command.add("--spring.config.location=config/application.yml");
    command.add("--server.port=" + port);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(runtime.toFile());
    builder.redirectOutput(smokeDir.resolve("logs").resolve("stdout.log").toFile());
    builder.redirectError(smokeDir.resolve("logs").resolve("stderr.log").toFile());
    process = builder.start();
  }

  private void waitReady() throws Exception {
    long deadline = System.currentTimeMillis() + 90000L;
    while (System.currentTimeMillis() < deadline) {
      HttpResult result = request("GET", "/api/saga/summary");
      if (result.status == 200) {
        checks.add("SAGA_HOST_READY");
        return;
      }
      Thread.sleep(1000L);
    }
    throw new IllegalStateException("host not ready");
  }

  private void checkNormalOrder() throws Exception {
    HttpResult created = request("POST", "/api/saga/orders?amount=12.50");
    assertStatus(created, 200, "create order");
    String orderId = stringField(created.body, "ORDER_ID");
    HttpResult tick = request("POST", "/api/saga/dispatcher/tick");
    assertStatus(tick, 200, "dispatch normal order");
    HttpResult order = request("GET", "/api/saga/orders/" + orderId);
    if (!order.body.contains("\"STATUS\":\"PAID\"")) {
      throw new IllegalStateException("order was not paid: " + order.body);
    }
    HttpResult before = request("GET", "/api/saga/summary");
    request("POST", "/api/saga/dispatcher/tick");
    HttpResult after = request("GET", "/api/saga/summary");
    if (numberField(before.body, "inbox") != numberField(after.body, "inbox")) {
      throw new IllegalStateException("duplicate tick changed inbox count");
    }
    checks.add("SAGA_ORDER_PAID");
    checks.add("SAGA_INBOX_IDEMPOTENT");
  }

  private void checkRetryOrder() throws Exception {
    HttpResult created = request("POST", "/api/saga/orders?amount=8.00&failBillingOnce=true");
    assertStatus(created, 200, "create retry order");
    String orderId = stringField(created.body, "ORDER_ID");
    HttpResult firstTick = request("POST", "/api/saga/dispatcher/tick");
    if (!firstTick.body.contains("\"dispatchStatus\":\"RETRY\"")) {
      throw new IllegalStateException("first tick did not request retry: " + firstTick.body);
    }
    HttpResult secondTick = request("POST", "/api/saga/dispatcher/tick");
    if (!secondTick.body.contains("\"dispatchStatus\":\"SENT\"")) {
      throw new IllegalStateException("second tick did not send event: " + secondTick.body);
    }
    HttpResult order = request("GET", "/api/saga/orders/" + orderId);
    if (!order.body.contains("\"STATUS\":\"PAID\"")) {
      throw new IllegalStateException("retry order was not paid: " + order.body);
    }
    checks.add("SAGA_RETRY_SUCCESS");
  }

  private HttpResult request(String method, String path) throws Exception {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(20000);
      int status = connection.getResponseCode();
      BufferedReader reader = new BufferedReader(new InputStreamReader(
          status >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder body = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        body.append(line);
      }
      return new HttpResult(status, body.toString());
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

  private String stringField(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    if (!matcher.find()) {
      throw new IllegalStateException("missing field " + field + " in " + json);
    }
    return matcher.group(1);
  }

  private long numberField(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9]+)").matcher(json);
    if (!matcher.find()) {
      throw new IllegalStateException("missing field " + field + " in " + json);
    }
    return Long.parseLong(matcher.group(1));
  }

  private void writeReport(String status) throws Exception {
    if (resultPath == null) {
      return;
    }
    Files.createDirectories(resultPath.toAbsolutePath().getParent());
    FileWriter writer = new FileWriter(resultPath.toFile());
    try {
      writer.write("{\"status\":\"" + status + "\",\"checks\":[");
      for (int i = 0; i < checks.size(); i++) {
        if (i > 0) {
          writer.write(",");
        }
        writer.write("\"" + checks.get(i) + "\"");
      }
      writer.write("]}");
    } finally {
      writer.close();
    }
  }

  private void stopHost() {
    if (process == null) {
      return;
    }
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

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private static class HttpResult {
    private final int status;
    private final String body;

    private HttpResult(int status, String body) {
      this.status = status;
      this.body = body == null ? "" : body;
    }
  }
}
