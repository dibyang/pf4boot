package net.xdob.sample.workflow;

import net.xdob.sample.model.audit.WorkflowAudit;
import net.xdob.sample.userbook.UserBookService;
import net.xdob.sample.workflow.repository.WorkflowAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流编排服务实现。
 *
 * <p>该服务通过跨插件导出的 UserBookService 完成业务组合，并通过独立 writer bean 演示
 * `REQUIRES_NEW` 必须经 Spring 代理调用才能生效。</p>
 */
@Service
public class WorkflowServiceImpl implements WorkflowService {

  @Autowired
  private UserBookService userBookService;

  @Autowired
  private WorkflowAuditWriter auditWriter;

  @Autowired
  private WorkflowAuditRepository auditRepository;

  @Override
  @Transactional(transactionManager = "domain.demo.transactionManager")
  public void place(String username, String password, String bookName, boolean failAfterAudit) {
    userBookService.registerUserWithBook(username, password, bookName);
    auditWriter.append(username, "place", bookName);
    if (failAfterAudit) {
      throw new IllegalStateException("sample workflow forced failure");
    }
  }

  @Override
  public Map<String, Object> summary() {
    Map<String, Object> result = new HashMap<>();
    result.put("users", userBookService.listUsers().size());
    result.put("books", userBookService.listBooks().size());
    result.put("audits", auditRepository.count());
    return result;
  }

  @Override
  public List<WorkflowAudit> audit(String username) {
    return auditRepository.findByUsername(username);
  }
}
