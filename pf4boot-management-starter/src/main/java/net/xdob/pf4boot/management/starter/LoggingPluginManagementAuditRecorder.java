package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPluginManagementAuditRecorder implements PluginManagementAuditRecorder {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPluginManagementAuditRecorder.class);

  @Override
  public void record(PluginManagementAuditEvent event) {
    if (event == null) {
      return;
    }
    LOGGER.info(
        "Management operation: requestId={}, operationId={}, operation={}, principalId={}, pluginId={}, deploymentId={}, success={}, code={}",
        event.getRequestId(),
        event.getOperationId(),
        event.getOperation(),
        event.getPrincipalId(),
        event.getPluginId(),
        event.getDeploymentId(),
        event.isSuccess(),
        event.getCode());
  }
}

