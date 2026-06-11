package net.xdob.sample.workflow;

import net.xdob.sample.model.audit.WorkflowAudit;

import java.util.List;
import java.util.Map;

/**
 * 工作流编排服务。
 */
public interface WorkflowService {

  void place(String username, String password, String bookName, boolean failAfterAudit);

  Map<String, Object> summary();

  List<WorkflowAudit> audit(String username);
}
