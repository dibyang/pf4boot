package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.DeploymentPlan;
import net.xdob.pf4boot.deployment.DeploymentState;
import net.xdob.pf4boot.deployment.PluginDeploymentService;
import net.xdob.pf4boot.deployment.RollbackSnapshot;
import net.xdob.pf4boot.management.PluginAdminResponse;
import net.xdob.pf4boot.management.PluginDeploymentRequest;
import net.xdob.pf4boot.management.PluginManagementAuditEvent;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementMetricsSnapshot;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import org.junit.Test;
import org.pf4j.PluginState;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class PluginManagementControllerTest {

  @Test
  public void startEndpointInvokesPluginManagerStart() {
    Pf4bootManagementProperties properties = properties();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), null);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/plugins/sample-workflow/start");

    try {
      controller.start("sample-workflow", request);
      fail("expected plugin-not-found because no runtime snapshot loaded");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.NOT_FOUND, e.getCode());
    }

    assertEquals(1, invocation.getCount("startPlugin"));
    assertEquals(1, invocation.getCount("getPlugin"));
  }

  @Test
  public void stopEndpointInvokesPluginManagerStop() {
    Pf4bootManagementProperties properties = properties();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), null);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/plugins/sample-workflow/stop");

    try {
      controller.stop("sample-workflow", request);
      fail("expected plugin-not-found because no runtime snapshot loaded");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.NOT_FOUND, e.getCode());
    }
    assertEquals(1, invocation.getCount("stopPlugin"));
  }

  @Test
  public void restartEndpointInvokesPluginManagerRestart() {
    Pf4bootManagementProperties properties = properties();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), null);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/plugins/sample-workflow/restart");

    try {
      controller.restart("sample-workflow", request);
      fail("expected plugin-not-found because no runtime snapshot loaded");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.NOT_FOUND, e.getCode());
    }
    assertEquals(1, invocation.getCount("restartPlugin"));
  }

  @Test
  public void planEndpointCallsPlanReplacementService() {
    Pf4bootManagementProperties properties = properties();
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), deploymentService);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/plan");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    PluginAdminResponse<DeploymentRecord> response = controller.plan(body, request);
    assertNotNull(response);
    assertNotNull(response.getOperationId());
    assertEquals(true, response.getOperationId().startsWith("op-"));
    assertEquals("plan-result", response.getData().getDeploymentId());
    assertEquals(1, deploymentService.planCalls);
    assertEquals(0, deploymentService.replaceCalls);
  }

  @Test
  public void replaceEndpointUsesPlanWhenDryRunDefaultIsTrue() {
    Pf4bootManagementProperties properties = properties();
    properties.setDryRunDefault(true);
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), deploymentService);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/replace");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");
    body.setDryRun(null);

    PluginAdminResponse<DeploymentRecord> response = controller.replace(body, request);
    assertNotNull(response);
    assertEquals("plan-result", response.getData().getDeploymentId());
    assertEquals(1, deploymentService.planCalls);
    assertEquals(0, deploymentService.replaceCalls);
  }

  @Test
  public void replaceEndpointCallsReplaceServiceWhenDryRunFalse() {
    Pf4bootManagementProperties properties = properties();
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();
    InvocationRecorder invocation = new InvocationRecorder();

    PluginManagementController controller = controller(properties, invocation.createPluginManager(), deploymentService);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/replace");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");
    body.setDryRun(false);

    PluginAdminResponse<DeploymentRecord> response = controller.replace(body, request);
    assertNotNull(response);
    assertEquals("op-replace", response.getData().getDeploymentId());
    assertEquals(0, deploymentService.planCalls);
    assertEquals(1, deploymentService.replaceCalls);
  }

  @Test
  public void writeSecurityRejectionIsAudited() {
    Pf4bootManagementProperties properties = properties();
    properties.getCsrf().setEnabled("true");
    CapturingAuditRecorder auditRecorder = new CapturingAuditRecorder();
    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), new RecordingDeploymentService(),
            new InMemoryPluginDeploymentRecordStore(), auditRecorder);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/replace");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    try {
      controller.replace(body, request);
      fail("expected CSRF rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
    }

    assertEquals(1, auditRecorder.events.size());
    assertEquals(PluginManagementOperation.DEPLOYMENT_REPLACE, auditRecorder.events.get(0).getOperation());
    assertEquals(PluginManagementErrorCode.FORBIDDEN.getCode(), auditRecorder.events.get(0).getCode());
  }

  @Test
  public void deploymentResponseAndAuditMessageAreSanitized() {
    Pf4bootManagementProperties properties = properties();
    CapturingAuditRecorder auditRecorder = new CapturingAuditRecorder();
    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), new SensitiveDeploymentService(),
            new InMemoryPluginDeploymentRecordStore(), auditRecorder);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/replace");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");
    body.setDryRun(false);

    PluginAdminResponse<DeploymentRecord> response = controller.replace(body, request);

    assertNotNull(response);
    assertEquals(1, auditRecorder.events.size());
    assertSanitized(response.getMessage());
    assertSanitized(auditRecorder.events.get(0).getMessage());
  }

  @Test
  public void deploymentQueriesReturnRecentRecordsOrderedByUpdatedAt() {
    Pf4bootManagementProperties properties = properties();
    InMemoryPluginDeploymentRecordStore store = new InMemoryPluginDeploymentRecordStore();
    store.save(
        new DeploymentRecord(
            "d1",
            "plug-a",
            DeploymentState.SUCCEEDED,
            0L,
            200L,
            "ok",
            null));
    store.save(
        new DeploymentRecord(
            "d2",
            "plug-b",
            DeploymentState.FAILED,
            0L,
            500L,
            "failed",
            null));

    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), new RecordingDeploymentService(), store);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments");

    PluginAdminResponse<List<DeploymentRecord>> response = controller.deployments(request);
    assertNotNull(response);
    assertEquals(2, response.getData().size());
    assertEquals("d2", response.getData().get(0).getDeploymentId());
    assertEquals("d1", response.getData().get(1).getDeploymentId());
  }

  @Test
  public void pluginsEndpointRecordOperationIdInAuditEvent() {
    Pf4bootManagementProperties properties = properties();
    CapturingAuditRecorder auditRecorder = new CapturingAuditRecorder();

    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), null,
            new InMemoryPluginDeploymentRecordStore(), auditRecorder);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/plugins");

    PluginAdminResponse<List<PluginRuntimeSnapshot>> response = controller.plugins(request);

    assertNotNull(response);
    assertEquals(1, auditRecorder.events.size());
    assertEquals(response.getOperationId(), auditRecorder.events.get(0).getOperationId());
  }

  @Test
  public void deploymentQueryByIdReturnsNotFoundForMissingRecord() {
    Pf4bootManagementProperties properties = properties();
    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), null);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/none");

    try {
      controller.deployment("none", request);
      fail("expected deployment not found");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.NOT_FOUND, e.getCode());
    }
  }

  @Test
  public void deploymentQueryByIdReturnsRecord() {
    Pf4bootManagementProperties properties = properties();
    InMemoryPluginDeploymentRecordStore store = new InMemoryPluginDeploymentRecordStore();
    store.save(
        new DeploymentRecord(
            "d-1",
            "plug-a",
            DeploymentState.SUCCEEDED,
            1000L,
            1000L,
            "query ok",
            null));

    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), null, store);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/d-1");

    PluginAdminResponse<DeploymentRecord> response = controller.deployment("d-1", request);

    assertNotNull(response);
    assertEquals("d-1", response.getData().getDeploymentId());
    assertEquals(DeploymentState.SUCCEEDED, response.getData().getState());
  }

  @Test
  public void confirmEndpointExecutesReplacementForPrecheckedRecord() {
    Pf4bootManagementProperties properties = properties();
    properties.setStagingRoot("target/test-staged");
    InMemoryPluginDeploymentRecordStore store = new InMemoryPluginDeploymentRecordStore();
    DeploymentPlan plan = new DeploymentPlan(
        "dep-1",
        "sample-workflow",
        "deployments/sample-workflow.zip",
        "plugins/sample-workflow-old.jar",
        "1.0.0",
        org.pf4j.PluginState.STOPPED,
        "2.0.0",
        null,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<DeploymentCheckResult>emptyList(),
        new RollbackSnapshot(
            "sample-workflow",
            "/tmp/sample-workflow.jar",
            "1.0.0",
            org.pf4j.PluginState.STOPPED,
            Collections.<String>emptyList(),
            Collections.<String, String>emptyMap()));
    store.save(new DeploymentRecord(
        "dep-1",
        "sample-workflow",
        DeploymentState.PRECHECKED,
        0L,
        0L,
        "plan ok",
        plan));
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();

    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), deploymentService, store);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/dep-1/confirm");

    PluginAdminResponse<DeploymentRecord> response = controller.confirm("dep-1", request);

    assertNotNull(response);
    assertEquals(1, deploymentService.replaceCalls);
    assertEquals("op-replace", response.getData().getDeploymentId());
    assertEquals("sample-workflow", deploymentService.lastReplacePluginId);
  }

  @Test
  public void confirmEndpointRejectsMissingDeploymentRecord() {
    Pf4bootManagementProperties properties = properties();
    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), new RecordingDeploymentService());
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/nope/confirm");

    try {
      controller.confirm("nope", request);
      fail("expected missing deployment id to be rejected");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.NOT_FOUND, e.getCode());
    }
  }

  @Test
  public void confirmEndpointRejectsUnprecheckedDeploymentRecord() {
    Pf4bootManagementProperties properties = properties();
    InMemoryPluginDeploymentRecordStore store = new InMemoryPluginDeploymentRecordStore();
    store.save(new DeploymentRecord(
        "dep-2",
        "sample-workflow",
        DeploymentState.SUCCEEDED,
        0L,
        0L,
        "already replaced",
        null));
    PluginManagementController controller =
        controller(properties, new InvocationRecorder().createPluginManager(), new RecordingDeploymentService(), store);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/dep-2/confirm");

    try {
      controller.confirm("dep-2", request);
      fail("expected prechecked-only confirm to be rejected");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.PRECHECK_FAILED, e.getCode());
    }
  }

  @Test
  public void planEndpointSupportsIdempotencyReplayWithSameKey() {
    Pf4bootManagementProperties properties = properties();
    properties.setRequireIdempotencyKey(true);
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();
    InvocationRecorder invocation = new InvocationRecorder();
    PluginManagementController controller = controller(properties, invocation.createPluginManager(), deploymentService);

    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/plan");
    request.addHeader(properties.getIdempotencyHeader(), "idem-1");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    PluginAdminResponse<DeploymentRecord> first = controller.plan(body, request);
    PluginAdminResponse<DeploymentRecord> second = controller.plan(body, request);
    assertEquals(first.getOperationId(), second.getOperationId());
    assertEquals(1, deploymentService.planCalls);
    assertNotNull(first.getOperationId());
  }

  @Test
  public void managementMetricsCountsRequestsAndIdempotencyReplay() {
    Pf4bootManagementProperties properties = properties();
    properties.setRequireIdempotencyKey(true);
    RecordingDeploymentService deploymentService = new RecordingDeploymentService();
    DefaultPluginManagementMetricsRecorder metricsRecorder = new DefaultPluginManagementMetricsRecorder();
    PluginManagementController controller = controller(
        properties,
        new InvocationRecorder().createPluginManager(),
        deploymentService,
        new InMemoryPluginDeploymentRecordStore(),
        new LoggingPluginManagementAuditRecorder(),
        metricsRecorder);

    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/plan");
    request.addHeader(properties.getIdempotencyHeader(), "idem-metrics");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    controller.plan(body, request);
    controller.plan(body, request);

    PluginManagementMetricsSnapshot snapshot = metricsRecorder.snapshot();
    assertEquals(2, snapshot.getRequestTotal());
    assertEquals(0, snapshot.getRejectedTotal());
    assertEquals(1, snapshot.getIdempotencyHitTotal());
  }

  @Test
  public void managementMetricsCountsWriteSecurityRejection() {
    Pf4bootManagementProperties properties = properties();
    properties.getCsrf().setEnabled("true");
    DefaultPluginManagementMetricsRecorder metricsRecorder = new DefaultPluginManagementMetricsRecorder();
    PluginManagementController controller = controller(
        properties,
        new InvocationRecorder().createPluginManager(),
        new RecordingDeploymentService(),
        new InMemoryPluginDeploymentRecordStore(),
        new LoggingPluginManagementAuditRecorder(),
        metricsRecorder);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/replace");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    try {
      controller.replace(body, request);
      fail("expected CSRF rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
    }

    PluginManagementMetricsSnapshot snapshot = metricsRecorder.snapshot();
    assertEquals(1, snapshot.getRequestTotal());
    assertEquals(1, snapshot.getRejectedTotal());
    assertEquals(0, snapshot.getIdempotencyHitTotal());
  }

  @Test
  public void managementMetricsCountsDeploymentPrecheckFailureResponseAsRejection() {
    Pf4bootManagementProperties properties = properties();
    DefaultPluginManagementMetricsRecorder metricsRecorder = new DefaultPluginManagementMetricsRecorder();
    PluginManagementController controller = controller(
        properties,
        new InvocationRecorder().createPluginManager(),
        new FailingPlanDeploymentService(),
        new InMemoryPluginDeploymentRecordStore(),
        new LoggingPluginManagementAuditRecorder(),
        metricsRecorder);
    MockHttpServletRequest request = baseRequest("/pf4boot/admin/deployments/plan");
    PluginDeploymentRequest body = deploymentRequest("sample-workflow", "plugin.jar");

    PluginAdminResponse<DeploymentRecord> response = controller.plan(body, request);

    assertEquals(false, response.isSuccess());
    PluginManagementMetricsSnapshot snapshot = metricsRecorder.snapshot();
    assertEquals(1, snapshot.getRequestTotal());
    assertEquals(1, snapshot.getRejectedTotal());
    assertEquals(0, snapshot.getIdempotencyHitTotal());
  }

  private PluginManagementController controller(
      Pf4bootManagementProperties properties,
      Pf4bootPluginManager pluginManager,
      RecordingDeploymentService deploymentService) {
    return controller(properties, pluginManager, deploymentService, new InMemoryPluginDeploymentRecordStore());
  }

  private PluginManagementController controller(
      Pf4bootManagementProperties properties,
      Pf4bootPluginManager pluginManager,
      RecordingDeploymentService deploymentService,
      InMemoryPluginDeploymentRecordStore store) {
    if (deploymentService == null) {
      deploymentService = new RecordingDeploymentService();
    }
    PluginManagementRequestFactory requestFactory = new PluginManagementRequestFactory();
    PluginManagementPathValidator pathValidator = new PluginManagementPathValidator();
    InMemoryPluginOperationStore operationStore = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService idempotencyService =
        new PluginManagementIdempotencyService(properties, operationStore);
    PluginManagementAuditRecorder auditRecorder = new LoggingPluginManagementAuditRecorder();
    PluginManagementRateLimiter rateLimiter = new PluginManagementRateLimiter(properties);
    PluginManagementWriteSecurityPolicy policy = new PluginManagementWriteSecurityPolicy(properties, rateLimiter);

    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        new LocalTokenPluginManagementAuthorizer(properties),
        requestFactory,
        pathValidator,
        idempotencyService,
        store,
        auditRecorder,
        operationStore,
        policy);
  }

  private PluginManagementController controller(
      Pf4bootManagementProperties properties,
      Pf4bootPluginManager pluginManager,
      RecordingDeploymentService deploymentService,
      InMemoryPluginDeploymentRecordStore store,
      PluginManagementAuditRecorder auditRecorder) {
    return controller(properties, pluginManager, deploymentService, store, auditRecorder,
        new DefaultPluginManagementMetricsRecorder());
  }

  private PluginManagementController controller(
      Pf4bootManagementProperties properties,
      Pf4bootPluginManager pluginManager,
      RecordingDeploymentService deploymentService,
      InMemoryPluginDeploymentRecordStore store,
      PluginManagementAuditRecorder auditRecorder,
      DefaultPluginManagementMetricsRecorder managementMetricsRecorder) {
    if (deploymentService == null) {
      deploymentService = new RecordingDeploymentService();
    }
    PluginManagementRequestFactory requestFactory = new PluginManagementRequestFactory();
    PluginManagementPathValidator pathValidator = new PluginManagementPathValidator();
    InMemoryPluginOperationStore operationStore = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService idempotencyService =
        new PluginManagementIdempotencyService(properties, operationStore);
    PluginManagementRateLimiter rateLimiter = new PluginManagementRateLimiter(properties);
    PluginManagementWriteSecurityPolicy policy = new PluginManagementWriteSecurityPolicy(properties, rateLimiter);

    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        new LocalTokenPluginManagementAuthorizer(properties),
        requestFactory,
        pathValidator,
        idempotencyService,
        store,
        auditRecorder,
        operationStore,
        policy,
        managementMetricsRecorder);
  }

  private Pf4bootManagementProperties properties() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("test-token");
    properties.setMode(net.xdob.pf4boot.management.PluginManagementMode.LOCAL_TOKEN);
    properties.getRateLimit().setEnabled(false);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.setStagingRoot("target/test-staged");
    return properties;
  }

  private MockHttpServletRequest baseRequest(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI(uri);
    request.setRemoteAddr("127.0.0.1");
    request.setServerName("127.0.0.1");
    request.setServerPort(8080);
    request.setScheme("http");
    request.setRemotePort(1111);
    request.addHeader("Host", "127.0.0.1:8080");
    request.addHeader("X-PF4Boot-Admin-Token", "test-token");
    request.setContentType("application/json");
    request.addHeader("Content-Length", "0");
    return request;
  }

  private PluginDeploymentRequest deploymentRequest(String pluginId, String stagedPath) {
    PluginDeploymentRequest request = new PluginDeploymentRequest();
    request.setPluginId(pluginId);
    request.setStagedPluginPath(stagedPath);
    request.setDryRun(true);
    return request;
  }

  private static class RecordingDeploymentService implements PluginDeploymentService {

    private int planCalls;
    private int replaceCalls;
    private String lastReplacePluginId;

    @Override
    public DeploymentRecord planReplacement(String targetPluginId, java.nio.file.Path stagedPluginPath) {
      planCalls++;
      return new DeploymentRecord(
          "plan-result",
          targetPluginId,
          DeploymentState.PRECHECKED,
          1L,
          1L,
          "plan ok",
          null);
    }

    @Override
    public DeploymentRecord replace(String targetPluginId, java.nio.file.Path stagedPluginPath) {
      replaceCalls++;
      lastReplacePluginId = targetPluginId;
      return new DeploymentRecord(
          "op-replace",
          targetPluginId,
          DeploymentState.SUCCEEDED,
          1L,
          1L,
          "replace ok",
          null);
    }
  }

  private static class SensitiveDeploymentService extends RecordingDeploymentService {
    @Override
    public DeploymentRecord replace(String targetPluginId, java.nio.file.Path stagedPluginPath) {
      return new DeploymentRecord(
          "sensitive-deployment",
          targetPluginId,
          DeploymentState.SUCCEEDED,
          1L,
          1L,
          "replaced from D:\\secret\\plugin.zip token=sample-token\n"
              + "    at com.example.Secret.run(Secret.java:1)",
          null);
    }
  }

  private static class FailingPlanDeploymentService extends RecordingDeploymentService {
    @Override
    public DeploymentRecord planReplacement(String targetPluginId, java.nio.file.Path stagedPluginPath) {
      return new DeploymentRecord(
          "failed-plan",
          targetPluginId,
          DeploymentState.FAILED,
          1L,
          1L,
          "precheck failed",
          null,
          DeploymentRecord.history(DeploymentState.PLANNED, DeploymentState.FAILED),
          0L,
          "PRECHECK_FAILED");
    }
  }

  private void assertSanitized(String value) {
    assertFalse(value.contains("sample-token"));
    assertFalse(value.contains("D:\\secret\\plugin.zip"));
    assertFalse(value.contains("com.example.Secret.run"));
  }

  private static class InvocationRecorder {
    private final List<String> invokedMethods = new ArrayList<String>();
    private final List<List<Object>> invokedArgs = new ArrayList<List<Object>>();

    private int getCount(String method) {
      int count = 0;
      for (String item : invokedMethods) {
        if (method.equals(item)) {
          count++;
        }
      }
      return count;
    }

    private Pf4bootPluginManager createPluginManager() {
      InvocationHandler handler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
          if (Object.class.equals(method.getDeclaringClass())) {
            if ("toString".equals(method.getName())) {
              return "plugin-manager-proxy";
            }
            if ("hashCode".equals(method.getName())) {
              return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
              return proxy == args[0];
            }
            return null;
          }
          String name = method.getName();
          invokedMethods.add(name);
          if (args != null) {
            invokedArgs.add(Arrays.asList(args));
          } else {
            invokedArgs.add(Collections.<Object>emptyList());
          }
          if (name.equals("getPlugin")) {
            return null;
          }
          if (name.equals("getPlugins")) {
            return Collections.emptyList();
          }
          if (name.equals("startPlugin") || name.equals("stopPlugin") || name.equals("restartPlugin")) {
            return PluginState.STARTED;
          }
          if (method.getReturnType().isPrimitive()) {
            if (method.getReturnType().equals(Boolean.TYPE)) {
              return false;
            }
            if (method.getReturnType().equals(Integer.TYPE)) {
              return 0;
            }
            if (method.getReturnType().equals(Long.TYPE)) {
              return 0L;
            }
          }
          return null;
        }
      };
      return (Pf4bootPluginManager) Proxy.newProxyInstance(
          Thread.currentThread().getContextClassLoader(),
          new Class<?>[]{Pf4bootPluginManager.class},
          handler);
    }
  }

  private static class CapturingAuditRecorder implements PluginManagementAuditRecorder {

    private final List<PluginManagementAuditEvent> events = new ArrayList<PluginManagementAuditEvent>();

    @Override
    public void record(PluginManagementAuditEvent event) {
      events.add(event);
    }
  }
}
