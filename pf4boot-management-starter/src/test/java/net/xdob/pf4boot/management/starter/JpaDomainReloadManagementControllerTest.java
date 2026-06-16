package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JpaDomainReloadManagementControllerTest {

  @Test
  public void reloadPlanRejectsProviderReplacementPathOutsideStagingRoot() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
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
