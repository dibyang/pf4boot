package net.xdob.pf4boot.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一 HTTP 管理接口响应模型.
 */
public class PluginAdminResponse<T> {

  private boolean success;
  private String requestId;
  private String operationId;
  private String code;
  private String message;
  private T data;
  private List<String> warnings = new ArrayList<>();

  public static <T> PluginAdminResponse<T> ok(
      String requestId,
      String operationId,
      String message,
      T data) {
    return ok(requestId, operationId, message, data, Collections.<String>emptyList());
  }

  public static <T> PluginAdminResponse<T> ok(
      String requestId,
      String operationId,
      String message,
      T data,
      List<String> warnings) {
    PluginAdminResponse<T> response = new PluginAdminResponse<>();
    response.setSuccess(true);
    response.setRequestId(requestId);
    response.setOperationId(operationId);
    response.setCode("OK");
    response.setMessage(message);
    response.setData(data);
    response.setWarnings(warnings);
    return response;
  }

  public static <T> PluginAdminResponse<T> failed(
      String requestId,
      String operationId,
      PluginManagementErrorCode errorCode,
      String message) {
    return failed(requestId, operationId, errorCode, message, Collections.<String>emptyList());
  }

  public static <T> PluginAdminResponse<T> failed(
      String requestId,
      String operationId,
      PluginManagementErrorCode errorCode,
      String message,
      List<String> warnings) {
    PluginAdminResponse<T> response = new PluginAdminResponse<>();
    response.setSuccess(false);
    response.setRequestId(requestId);
    response.setOperationId(operationId);
    response.setCode(errorCode == null ? "PFM-ERROR" : errorCode.getCode());
    response.setMessage(message);
    response.setWarnings(warnings);
    return response;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings == null ? new ArrayList<String>() : warnings;
  }
}
