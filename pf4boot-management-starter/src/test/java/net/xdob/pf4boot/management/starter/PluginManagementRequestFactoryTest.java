package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PluginManagementRequestFactoryTest {

  @Test
  public void toPluginRequestCapturesHeadersAndPath() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRequestURI("/pf4boot/admin/plugins/p1/start");
    servletRequest.setRemoteAddr("127.0.0.1");
    servletRequest.setMethod("POST");
    servletRequest.setRemotePort(12345);
    servletRequest.addHeader("X-Request-Id", "req-123");
    servletRequest.addHeader("X-Idempotency-Key", "k-001");
    servletRequest.addHeader("X-PF4Boot-Admin-Token", "tok");
    servletRequest.addHeader("Origin", "http://127.0.0.1:8080");

    PluginManagementRequestFactory factory = new PluginManagementRequestFactory();
    PluginManagementRequest request = factory.toPluginRequest(servletRequest, PluginManagementOperation.PLUGIN_START, "p1", null,
        properties);

    assertEquals("req-123", request.getRequestId());
    assertEquals("k-001", request.getIdempotencyKey());
    assertEquals("tok", request.getToken());
    assertEquals("127.0.0.1", request.getRemoteAddress());
    assertEquals("http://127.0.0.1:8080", request.getOrigin());
    assertEquals("/pf4boot/admin/plugins/p1/start", request.getPath());
    assertEquals("POST", request.getMethod());
  }

  @Test
  public void toPluginRequestGeneratesRequestIdWhenMissing() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRemoteAddr("127.0.0.1");
    servletRequest.setMethod("GET");
    servletRequest.setRequestURI("/pf4boot/admin/plugins");

    PluginManagementRequestFactory factory = new PluginManagementRequestFactory();
    PluginManagementRequest request = factory.toPluginRequest(servletRequest, PluginManagementOperation.PLUGIN_READ, null, null,
        properties);

    assertEquals(PluginManagementOperation.PLUGIN_READ, request.getOperation());
    assertNull(request.getIdempotencyKey());
    assertNull(request.getToken());
    assertNotNull(request.getRequestId());
    assertTrue(request.getRequestId().startsWith("req-"));
    assertEquals("/pf4boot/admin/plugins", request.getPath());
  }
}
