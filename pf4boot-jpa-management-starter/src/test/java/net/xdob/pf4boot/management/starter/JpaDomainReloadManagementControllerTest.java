package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class JpaDomainReloadManagementControllerTest {

  @Test
  public void reloadPlanRejectsProviderReplacementPathOutsideStagingRoot() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(false);
    properties.setStagingRoot("build/staged");
    JpaDomainReloadManagementController controller = new JpaDomainReloadManagementController(
        request -> null,
        provider(null),
        properties,
        allowAllAuthorizer(),
        new PluginManagementRequestFactory(),
        event -> {
        },
        new PluginManagementPathValidator());
    JpaDomainReloadRequest body = new JpaDomainReloadRequest();
    body.setProviderReplacementPath("../outside/provider.zip");

    try {
      controller.plan("demo", body, new MockHttpServletRequest("POST", "/pf4boot/admin/jpa/domains/demo/reload/plan"));
      fail("Expected PluginManagementException");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.PRECHECK_FAILED, e.getCode());
    }
  }

  @Test
  public void planUsesJpaReloadPlanPermission() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(false);
    RecordingAuthorizer authorizer = new RecordingAuthorizer();
    JpaDomainReloadManagementController controller = new JpaDomainReloadManagementController(
        emptyPlanService(),
        provider(null),
        properties,
        authorizer,
        new PluginManagementRequestFactory(),
        event -> {
        },
        new PluginManagementPathValidator());

    controller.plan("demo", null, new MockHttpServletRequest("POST", "/pf4boot/admin/jpa/domains/demo/reload/plan"));

    assertEquals(PluginManagementOperation.JPA_RELOAD_PLAN, authorizer.operations.get(0));
  }

  @Test
  public void reloadUsesJpaReloadExecutePermissionAndIdempotency() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setToken("secret");
    properties.setAllowLoopbackOnly(false);
    InMemoryPluginOperationStore operationStore = new InMemoryPluginOperationStore();
    CountingReloadService service = new CountingReloadService();
    JpaDomainReloadManagementController controller = new JpaDomainReloadManagementController(
        emptyPlanService(),
        provider(service),
        properties,
        new LocalTokenPluginManagementAuthorizer(properties),
        new PluginManagementRequestFactory(),
        event -> {
        },
        new PluginManagementPathValidator(),
        new PluginManagementIdempotencyService(properties, operationStore),
        operationStore,
        new PluginManagementWriteSecurityPolicy(properties, new PluginManagementRateLimiter(properties)),
        new DefaultPluginManagementMetricsRecorder());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pf4boot/admin/jpa/domains/demo/reload");
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-PF4Boot-Admin-Token", "secret");
    request.addHeader("X-Idempotency-Key", "reload-key-1");
    JpaDomainReloadRequest body = new JpaDomainReloadRequest();
    body.setMode(JpaDomainReloadMode.STOP_CONSUMERS_AND_REBUILD);

    assertEquals("reload-1", controller.reload("demo", body, request).getData().getReloadId());
    assertEquals("reload-1", controller.reload("demo", body, request).getData().getReloadId());

    assertEquals(1, service.reloadCount);
    assertNotNull(operationStore.findByIdempotencyKey(
        "10.0.0.1:JPA_RELOAD_EXECUTE:demo:reload-key-1"));
  }

  @Test
  public void localTokenAuthorizerAllowsJpaReloadPermissions() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setToken("secret");
    properties.setAllowLoopbackOnly(false);
    LocalTokenPluginManagementAuthorizer authorizer = new LocalTokenPluginManagementAuthorizer(properties);
    PluginManagementRequest request = new PluginManagementRequest();
    request.setRemoteAddress("10.0.0.2");
    request.setToken("secret");

    PluginManagementPrincipal principal = authorizer.authenticate(request);

    authorizer.authorize(principal, PluginManagementOperation.JPA_RELOAD_PLAN);
    authorizer.authorize(principal, PluginManagementOperation.JPA_RELOAD_EXECUTE);
    authorizer.authorize(principal, PluginManagementOperation.JPA_RELOAD_QUERY);
  }

  @Test
  public void queryUsesJpaReloadQueryPermission() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(false);
    RecordingAuthorizer authorizer = new RecordingAuthorizer();
    CountingReloadService service = new CountingReloadService();
    service.lastRecord = record("reload-query", "demo");
    JpaDomainReloadManagementController controller = new JpaDomainReloadManagementController(
        emptyPlanService(),
        provider(service),
        properties,
        authorizer,
        new PluginManagementRequestFactory(),
        event -> {
        },
        new PluginManagementPathValidator());

    controller.record("reload-query", new MockHttpServletRequest("GET", "/pf4boot/admin/jpa/reloads/reload-query"));

    assertEquals(PluginManagementOperation.JPA_RELOAD_QUERY, authorizer.operations.get(0));
  }

  private static PluginManagementAuthorizer allowAllAuthorizer() {
    return new PluginManagementAuthorizer() {
      @Override
      public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
        return new PluginManagementPrincipal("tester");
      }

      @Override
      public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
      }
    };
  }

  private static JpaDomainReloadPlanService emptyPlanService() {
    return request -> new JpaDomainReloadPlan(
        "plan-1",
        request == null ? "demo" : request.getDomainId(),
        "provider",
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        System.currentTimeMillis(),
        true);
  }

  private static JpaDomainReloadRecord record(String reloadId, String domainId) {
    return new JpaDomainReloadRecord(
        reloadId,
        "plan-1",
        domainId,
        JpaDomainReloadState.SUCCEEDED,
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        null,
        null,
        Collections.singletonList(JpaDomainReloadState.SUCCEEDED),
        null,
        null,
        null);
  }

  private static class RecordingAuthorizer implements PluginManagementAuthorizer {
    private final List<PluginManagementOperation> operations = new ArrayList<>();

    @Override
    public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
      return new PluginManagementPrincipal("tester");
    }

    @Override
    public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
      operations.add(operation);
    }
  }

  private static class CountingReloadService implements JpaDomainReloadService {
    private int reloadCount;
    private JpaDomainReloadRecord lastRecord;

    @Override
    public JpaDomainReloadPlan plan(JpaDomainReloadRequest request) {
      return emptyPlanService().plan(request);
    }

    @Override
    public JpaDomainReloadRecord reload(JpaDomainReloadRequest request) {
      reloadCount++;
      lastRecord = record("reload-" + reloadCount, request.getDomainId());
      return lastRecord;
    }

    @Override
    public JpaDomainReloadRecord getRecord(String reloadId) {
      if (lastRecord != null && lastRecord.getReloadId().equals(reloadId)) {
        return lastRecord;
      }
      return null;
    }

    @Override
    public JpaDomainReloadRecord getCurrent(String domainId) {
      return lastRecord;
    }
  }

  private static ObjectProvider<JpaDomainReloadService> provider(JpaDomainReloadService service) {
    return new ObjectProvider<JpaDomainReloadService>() {
      @Override
      public JpaDomainReloadService getObject(Object... args) {
        return service;
      }

      @Override
      public JpaDomainReloadService getObject() {
        return service;
      }

      @Override
      public JpaDomainReloadService getIfAvailable() {
        return service;
      }

      @Override
      public JpaDomainReloadService getIfUnique() {
        return service;
      }
    };
  }
}
