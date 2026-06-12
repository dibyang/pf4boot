package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementAuditEvent;

public interface PluginManagementAuditRecorder {
  void record(PluginManagementAuditEvent event);
}

