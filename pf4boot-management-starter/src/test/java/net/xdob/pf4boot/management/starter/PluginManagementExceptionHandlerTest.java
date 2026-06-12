package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginAdminResponse;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PluginManagementExceptionHandlerTest {

  @Test
  public void pluginManagementExceptionResponseDoesNotExposeSensitiveMessage() {
    PluginManagementExceptionHandler handler = new PluginManagementExceptionHandler();

    ResponseEntity<PluginAdminResponse<Object>> response = handler.handlePluginManagementException(
        new PluginManagementException(
            PluginManagementErrorCode.OPERATION_FAILED,
            "failed at D:\\secret\\plugin.zip with token=sample-token",
            500));

    assertEquals(500, response.getStatusCodeValue());
    assertEquals("Management operation failed", response.getBody().getMessage());
    assertFalse(response.getBody().getMessage().contains("sample-token"));
    assertFalse(response.getBody().getMessage().contains("D:\\secret"));
  }

  @Test
  public void illegalArgumentResponseDoesNotExposeRawMessage() {
    PluginManagementExceptionHandler handler = new PluginManagementExceptionHandler();

    ResponseEntity<PluginAdminResponse<Object>> response = handler.handleBadRequest(
        new IllegalArgumentException("bad path C:\\private\\plugin.zip"));

    assertEquals(400, response.getStatusCodeValue());
    assertEquals("Invalid management request", response.getBody().getMessage());
    assertFalse(response.getBody().getMessage().contains("C:\\private"));
  }
}
