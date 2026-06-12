package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.DeploymentState;
import net.xdob.pf4boot.deployment.DeploymentPlan;
import net.xdob.pf4boot.deployment.PluginDeploymentService;
import net.xdob.pf4boot.deployment.RollbackSnapshot;
import net.xdob.pf4boot.management.PluginDeploymentRequest;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.pf4j.PluginState;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class PluginManagementControllerSecurityTest {

  @Test
  public void csrfEnabledRequiresOriginForWriteRequests() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("true");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(false);
    properties.setStagingRoot("target/test-staged");

    PluginManagementController controller = controller(properties);

    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");
    PluginDeploymentRequest body = new PluginDeploymentRequest();
    body.setPluginId("plug");
    body.setStagedPluginPath("plugin.jar");

    try {
      controller.plan(body, request);
      fail("Expected CSRF/Origin rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
    }
  }

  @Test
  public void rateLimitAppliedBeforeSecondWrite() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(true);
    properties.getRateLimit().setWritesPerMinute(1);
    properties.setStagingRoot("target/test-staged");

    PluginManagementController controller = controller(properties);

    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");
    request.addHeader("Origin", "http://127.0.0.1:8080");
    PluginDeploymentRequest body = new PluginDeploymentRequest();
    body.setPluginId("plug");
    body.setStagedPluginPath("plugin.jar");

    controller.plan(body, request);
    try {
      controller.plan(body, request);
      fail("Expected rate limit rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.RATE_LIMITED, e.getCode());
    }
  }

  @Test
  public void csrfEnabledRequiresOriginForRollbackWrite() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("true");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(false);
    properties.setStagingRoot("target/test-staged");

    PluginManagementController controller = controllerWithDeployment(
        properties,
        new DeploymentRecord(
            "rb-1",
            "plug",
            DeploymentState.SUCCEEDED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "bootstrap",
            rollbackPlan("plug", "rb-1")));

    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");

    try {
      controller.rollback("rb-1", request);
      fail("Expected CSRF/Origin rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
    }
  }

  @Test
  public void csrfEnabledRequiresOriginForConfirmWrite() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("true");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(false);
    properties.setStagingRoot("target/test-staged");
    InMemoryPluginDeploymentRecordStore recordStore = new InMemoryPluginDeploymentRecordStore();
    recordStore.save(precheckedRecord("conf-1"));

    PluginManagementController controller = controllerWithDeployment(
        properties,
        new LocalTokenPluginManagementAuthorizer(properties),
        recordStore,
        confirmingDeploymentService());

    MockHttpServletRequest request = baseRequest();
    request.setRequestURI("/pf4boot/admin/deployments/conf-1/confirm");
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");

    try {
      controller.confirm("conf-1", request);
      fail("Expected CSRF/Origin rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
    }
  }

  @Test
  public void rateLimitAppliedBeforeSecondConfirm() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(true);
    properties.getRateLimit().setWritesPerMinute(1);
    properties.setStagingRoot("target/test-staged");
    InMemoryPluginDeploymentRecordStore recordStore = new InMemoryPluginDeploymentRecordStore();
    recordStore.save(precheckedRecord("conf-2"));

    PluginManagementController controller = controllerWithDeployment(
        properties,
        new LocalTokenPluginManagementAuthorizer(properties),
        recordStore,
        confirmingDeploymentService());

    MockHttpServletRequest request = baseRequest();
    request.setRequestURI("/pf4boot/admin/deployments/conf-2/confirm");
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");
    request.addHeader("Origin", "http://127.0.0.1:8080");

    controller.confirm("conf-2", request);
    try {
      controller.confirm("conf-2", request);
      fail("Expected rate limit rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.RATE_LIMITED, e.getCode());
    }
  }

  @Test
  public void rateLimitAppliedBeforeSecondRollback() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(true);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(true);
    properties.getRateLimit().setWritesPerMinute(1);
    properties.setStagingRoot("target/test-staged");

    PluginManagementController controller = controllerWithDeployment(
        properties,
        new DeploymentRecord(
            "rb-2",
            "plug",
            DeploymentState.SUCCEEDED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "bootstrap",
            rollbackPlan("plug", "rb-2")));

    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-PF4Boot-Admin-Token", "secret-token");
    request.addHeader("Origin", "http://127.0.0.1:8080");

    controller.rollback("rb-2", request);
    try {
      controller.rollback("rb-2", request);
      fail("Expected rate limit rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.RATE_LIMITED, e.getCode());
    }
  }

  @Test
  public void remoteUnauthenticatedDelegatedRequestRejectedWith401() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(false);
    properties.setStagingRoot("target/test-staged");

    PluginManagementAuthorizer authorizer = new DelegatingPluginManagementAuthorizer(Collections
        .<PluginManagementAuthorizer>emptyList());
    PluginManagementController controller = controller(properties, authorizer);

    MockHttpServletRequest request = baseRequest();
    request.setRemoteAddr("127.0.0.1");
    request.setServerName("127.0.0.1");
    request.setServerPort(8080);
    request.addHeader("Host", "127.0.0.1:8080");
    request.addHeader("Origin", "http://127.0.0.1:8080");

    PluginDeploymentRequest body = new PluginDeploymentRequest();
    body.setPluginId("plug");
    body.setStagedPluginPath("plugin.jar");

    try {
      controller.plan(body, request);
      fail("Expected unauthenticated rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.UNAUTHENTICATED, e.getCode());
      assertNotNull(e.getMessage());
    }
  }

  @Test
  public void remoteUnauthorizedDelegatedRequestRejectedWith403() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.getCsrf().setEnabled("false");
    properties.setRequireIdempotencyKey(false);
    properties.getRateLimit().setEnabled(false);
    properties.setStagingRoot("target/test-staged");

    PluginManagementAuthorizer authorizer = new DelegatingPluginManagementAuthorizer(
        Collections.<PluginManagementAuthorizer>singletonList(
            new PluginManagementAuthorizer() {
              @Override
              public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
                PluginManagementPrincipal principal = new PluginManagementPrincipal();
                principal.setPrincipalId("remote-user");
                principal.setPrincipalName("remote-user");
                principal.setPermissions(Collections.singletonList("pf4boot:plugin:read"));
                return principal;
              }

              @Override
              public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
                throw new PluginManagementException(
                    PluginManagementErrorCode.FORBIDDEN,
                    "No policy for operation: " + operation,
                    403);
              }
            }));
    PluginManagementController controller = controller(properties, authorizer);

    MockHttpServletRequest request = baseRequest();
    request.setRemoteAddr("127.0.0.1");
    request.setServerName("127.0.0.1");
    request.setServerPort(8080);
    request.addHeader("Host", "127.0.0.1:8080");
    request.addHeader("Origin", "http://127.0.0.1:8080");

    PluginDeploymentRequest body = new PluginDeploymentRequest();
    body.setPluginId("plug");
    body.setStagedPluginPath("plugin.jar");

    try {
      controller.plan(body, request);
      fail("Expected unauthorized rejection");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
      assertNotNull(e.getMessage());
    }
  }

  @Test
  public void localTokenAuthorizerAllowsReadQueryAndRollbackPermissions() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setToken("secret-token");
    properties.setAllowLoopbackOnly(false);

    LocalTokenPluginManagementAuthorizer authorizer = new LocalTokenPluginManagementAuthorizer(properties);
    PluginManagementRequest request = new PluginManagementRequest();
    request.setToken("secret-token");
    request.setRemoteAddress("127.0.0.1");
    PluginManagementPrincipal principal = authorizer.authenticate(request);

    authorizer.authorize(principal, PluginManagementOperation.PLUGIN_READ);
    authorizer.authorize(principal, PluginManagementOperation.DEPLOYMENT_QUERY);
    authorizer.authorize(principal, PluginManagementOperation.DEPLOYMENT_ROLLBACK);
  }

  private PluginManagementController controller(Pf4bootManagementProperties properties) {
    return controller(properties, new LocalTokenPluginManagementAuthorizer(properties));
  }

  private PluginManagementController controller(Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer) {
    Pf4bootPluginManager pluginManager = (Pf4bootPluginManager) Proxy.newProxyInstance(
        Thread.currentThread().getContextClassLoader(),
        new Class<?>[]{Pf4bootPluginManager.class},
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
          }
        });

    PluginDeploymentService deploymentService = new PluginDeploymentService() {
      @Override
      public DeploymentRecord planReplacement(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        return new DeploymentRecord(
            "d-1",
            targetPluginId,
            DeploymentState.SUCCEEDED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "ok",
            null);
      }

      @Override
      public DeploymentRecord replace(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        return null;
      }
    };

    PluginManagementRequestFactory requestFactory = new PluginManagementRequestFactory();
    PluginManagementPathValidator pathValidator = new PluginManagementPathValidator();
    InMemoryPluginOperationStore operationStore = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService idempotencyService = new PluginManagementIdempotencyService(
        properties,
        operationStore);
    PluginDeploymentRecordStore deploymentRecordStore = new InMemoryPluginDeploymentRecordStore();
    PluginManagementAuditRecorder auditRecorder = new LoggingPluginManagementAuditRecorder();
    PluginManagementRateLimiter rateLimiter = new PluginManagementRateLimiter(properties);
    PluginManagementWriteSecurityPolicy policy = new PluginManagementWriteSecurityPolicy(properties, rateLimiter);

    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        authorizer,
        requestFactory,
        pathValidator,
        idempotencyService,
        deploymentRecordStore,
        auditRecorder,
        operationStore,
        policy);
  }

  private PluginManagementController controllerWithDeployment(
      Pf4bootManagementProperties properties,
      DeploymentRecord record) {
    InMemoryPluginDeploymentRecordStore recordStore = new InMemoryPluginDeploymentRecordStore();
    if (record != null) {
      recordStore.save(record);
    }
    return controllerWithDeployment(properties, new LocalTokenPluginManagementAuthorizer(properties), recordStore);
  }

  private PluginManagementController controllerWithDeployment(
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      InMemoryPluginDeploymentRecordStore recordStore,
      PluginDeploymentService deploymentService) {
    Pf4bootPluginManager pluginManager = (Pf4bootPluginManager) Proxy.newProxyInstance(
        Thread.currentThread().getContextClassLoader(),
        new Class<?>[]{Pf4bootPluginManager.class},
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
          }
        });

    PluginManagementRequestFactory requestFactory = new PluginManagementRequestFactory();
    PluginManagementPathValidator pathValidator = new PluginManagementPathValidator();
    PluginManagementIdempotencyService idempotencyService = new PluginManagementIdempotencyService(
        properties,
        new InMemoryPluginOperationStore());
    PluginManagementAuditRecorder auditRecorder = new LoggingPluginManagementAuditRecorder();
    PluginManagementRateLimiter rateLimiter = new PluginManagementRateLimiter(properties);
    PluginManagementWriteSecurityPolicy policy = new PluginManagementWriteSecurityPolicy(properties, rateLimiter);

    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        authorizer,
        requestFactory,
        pathValidator,
        idempotencyService,
        recordStore,
        auditRecorder,
        new InMemoryPluginOperationStore(),
        policy);
  }

  private PluginManagementController controllerWithDeployment(
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      InMemoryPluginDeploymentRecordStore recordStore) {
    Pf4bootPluginManager pluginManager = (Pf4bootPluginManager) Proxy.newProxyInstance(
        Thread.currentThread().getContextClassLoader(),
        new Class<?>[]{Pf4bootPluginManager.class},
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
          }
        });

    PluginDeploymentService deploymentService = new PluginDeploymentService() {
      @Override
      public DeploymentRecord planReplacement(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        return new DeploymentRecord(
            "d-1",
            targetPluginId,
            DeploymentState.SUCCEEDED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "ok",
            null);
      }

      @Override
      public DeploymentRecord replace(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        return null;
      }
    };

    PluginManagementRequestFactory requestFactory = new PluginManagementRequestFactory();
    PluginManagementPathValidator pathValidator = new PluginManagementPathValidator();
    PluginManagementIdempotencyService idempotencyService = new PluginManagementIdempotencyService(
        properties,
        new InMemoryPluginOperationStore());
    PluginManagementAuditRecorder auditRecorder = new LoggingPluginManagementAuditRecorder();
    PluginManagementRateLimiter rateLimiter = new PluginManagementRateLimiter(properties);
    PluginManagementWriteSecurityPolicy policy = new PluginManagementWriteSecurityPolicy(properties, rateLimiter);

    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        authorizer,
        requestFactory,
        pathValidator,
        idempotencyService,
        recordStore,
        auditRecorder,
        new InMemoryPluginOperationStore(),
        policy);
  }

  private DeploymentPlan rollbackPlan(String pluginId, String deploymentId) {
    RollbackSnapshot snapshot = new RollbackSnapshot(
        pluginId,
        "/tmp/" + pluginId + ".jar",
        "1.0.0",
        PluginState.STOPPED,
        Collections.<String>emptyList(),
        Collections.<String, String>emptyMap());
    return new DeploymentPlan(
        deploymentId,
        pluginId,
        "/tmp/" + pluginId + ".zip",
        "/tmp/" + pluginId + "-current.jar",
        "1.0.0",
        PluginState.STOPPED,
        "2.0.0",
        null,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<DeploymentCheckResult>emptyList(),
        snapshot);
  }

  private DeploymentRecord precheckedRecord(String deploymentId) {
    return new DeploymentRecord(
        deploymentId,
        "plugin",
        DeploymentState.PRECHECKED,
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        "precheck ok",
        new DeploymentPlan(
            deploymentId,
            "plugin",
            "plugins/staged/plugin.zip",
            "plugins/current/plugin.jar",
            "1.0.0",
            org.pf4j.PluginState.STOPPED,
            "2.0.0",
            null,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<DeploymentCheckResult>emptyList(),
            new RollbackSnapshot(
                "plugin",
                "plugins/current/plugin.jar",
                "1.0.0",
                org.pf4j.PluginState.STOPPED,
                Collections.<String>emptyList(),
                java.util.Collections.<String, String>emptyMap())));
  }

  private PluginDeploymentService confirmingDeploymentService() {
    return new PluginDeploymentService() {
      @Override
      public DeploymentRecord planReplacement(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        throw new UnsupportedOperationException("plan not needed");
      }

      @Override
      public DeploymentRecord replace(String targetPluginId, java.nio.file.Path stagedPluginPath) {
        return new DeploymentRecord(
            "conf-" + targetPluginId,
            targetPluginId,
            DeploymentState.SUCCEEDED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "confirm ok",
            null);
      }
    };
  }

  private MockHttpServletRequest baseRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/pf4boot/admin/deployments/plan");
    request.setRemoteAddr("127.0.0.1");
    request.setServerName("127.0.0.1");
    request.setServerPort(8080);
    request.setScheme("http");
    request.setRemotePort(1111);
    request.addHeader("Host", "127.0.0.1:8080");
    request.setContentType("application/json");
    return request;
  }
}
