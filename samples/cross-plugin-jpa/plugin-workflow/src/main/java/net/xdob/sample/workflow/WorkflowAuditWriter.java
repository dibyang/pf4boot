package net.xdob.sample.workflow;

/**
 * 工作流审计写入器。
 */
public interface WorkflowAuditWriter {

  void append(String username, String action, String target);
}
