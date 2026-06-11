package net.xdob.sample.workflow;

import net.xdob.sample.model.audit.WorkflowAudit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作流演示接口。
 */
@RestController
@RequestMapping("/api/sample/workflow")
public class WorkflowController {

  @Autowired
  private WorkflowService workflowService;

  @GetMapping("/place")
  public Map<String, Object> place(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam String bookName,
      @RequestParam(defaultValue = "false") boolean failAfterAudit) {
    workflowService.place(username, password, bookName, failAfterAudit);
    return workflowService.summary();
  }

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    return workflowService.summary();
  }

  @GetMapping("/audit")
  public List<WorkflowAudit> audit(@RequestParam String username) {
    return workflowService.audit(username);
  }
}
