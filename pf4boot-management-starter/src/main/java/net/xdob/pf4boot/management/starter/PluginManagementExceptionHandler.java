package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginAdminResponse;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 管理接口统一异常处理。
 */
@ControllerAdvice
public class PluginManagementExceptionHandler {

  @ExceptionHandler(PluginManagementException.class)
  public ResponseEntity<PluginAdminResponse<Object>> handlePluginManagementException(
      PluginManagementException e) {
    PluginAdminResponse<Object> response = PluginAdminResponse.failed(null, null, e.getCode(), e.getMessage());
    return new ResponseEntity<>(response, HttpStatus.valueOf(e.getStatusCode()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<PluginAdminResponse<Object>> handleBadRequest(IllegalArgumentException e) {
    PluginAdminResponse<Object> response = PluginAdminResponse.failed(
        null, null, PluginManagementErrorCode.INVALID_REQUEST, e.getMessage());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<PluginAdminResponse<Object>> handleUnexpected(Throwable e) {
    PluginAdminResponse<Object> response = PluginAdminResponse.failed(
        null, null, PluginManagementErrorCode.OPERATION_FAILED, "Internal management failure");
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}

