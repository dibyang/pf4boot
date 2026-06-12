package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PluginManagementWriteSecurityPolicyTest {

  @Test
  public void autoModeSkipsOriginIfNotBrowserRequest() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getCsrf().setEnabled("auto");
    properties.getRateLimit().setEnabled(false);

    PluginManagementRequest request = request();

    PluginManagementWriteSecurityPolicy policy = policy(properties);
    policy.validateWriteRequest(mockRequest(), request);
  }

  @Test
  public void trueModeRequiresOriginAndSameOrigin() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getCsrf().setEnabled("true");
    properties.getRateLimit().setEnabled(false);

    PluginManagementWriteSecurityPolicy policy = policy(properties);
    PluginManagementRequest request = request();

    MockHttpServletRequest servletRequest = mockRequest();
    servletRequest.addHeader("Origin", "http://127.0.0.1:8080");
    policy.validateWriteRequest(servletRequest, request);
  }

  @Test
  public void trueModeRejectsMissingOrigin() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getCsrf().setEnabled("true");
    properties.getRateLimit().setEnabled(false);

    PluginManagementWriteSecurityPolicy policy = policy(properties);
    PluginManagementRequest request = request();

    try {
      policy.validateWriteRequest(mockRequest(), request);
      fail("Expected CSRF origin check exception");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
      assertEquals(403, e.getStatusCode());
    }
  }

  @Test
  public void trueModeRejectsOriginMismatch() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getCsrf().setEnabled("true");
    properties.getRateLimit().setEnabled(false);

    PluginManagementWriteSecurityPolicy policy = policy(properties);
    PluginManagementRequest request = request();

    MockHttpServletRequest servletRequest = mockRequest();
    servletRequest.addHeader("Origin", "http://evil.example");
    servletRequest.addHeader("Host", "127.0.0.1:8080");
    servletRequest.setRequestURI("/pf4boot/admin/plugins/p1/start");
    servletRequest.setScheme("http");
    servletRequest.setServerName("127.0.0.1");
    servletRequest.setServerPort(8080);
    servletRequest.setRemoteAddr("127.0.0.1");
    try {
      policy.validateWriteRequest(servletRequest, request);
      fail("Expected CSRF origin check exception");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.FORBIDDEN, e.getCode());
      assertEquals(403, e.getStatusCode());
    }
  }

  private PluginManagementWriteSecurityPolicy policy(Pf4bootManagementProperties properties) {
    PluginManagementRateLimiter limiter = new PluginManagementRateLimiter(properties);
    return new PluginManagementWriteSecurityPolicy(properties, limiter);
  }

  private PluginManagementRequest request() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    MockHttpServletRequest mock = mockRequest();
    PluginManagementRequestFactory factory = new PluginManagementRequestFactory();
    return factory.toPluginRequest(mock, PluginManagementOperation.PLUGIN_START, "p1", null, properties);
  }

  private MockHttpServletRequest mockRequest() {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRequestURI("/pf4boot/admin/plugins/p1/start");
    servletRequest.setMethod("POST");
    servletRequest.setServerName("127.0.0.1");
    servletRequest.setServerPort(8080);
    servletRequest.setScheme("http");
    servletRequest.setRemoteAddr("127.0.0.1");
    servletRequest.addHeader("Host", "127.0.0.1:8080");
    servletRequest.setRemotePort(12345);
    return servletRequest;
  }
}
