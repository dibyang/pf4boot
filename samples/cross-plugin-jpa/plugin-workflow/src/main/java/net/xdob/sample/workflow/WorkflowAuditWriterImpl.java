package net.xdob.sample.workflow;

import net.xdob.sample.model.audit.WorkflowAudit;
import net.xdob.sample.workflow.repository.WorkflowAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作流审计写入实现。
 *
 * <p>独立 Bean 用于确保 `REQUIRES_NEW` 经 Spring 事务代理生效。</p>
 */
@Service
public class WorkflowAuditWriterImpl implements WorkflowAuditWriter {

  @Autowired
  private WorkflowAuditRepository auditRepository;

  @Override
  @Transactional(transactionManager = "domain.demo.transactionManager", propagation = Propagation.REQUIRES_NEW)
  public void append(String username, String action, String target) {
    WorkflowAudit audit = new WorkflowAudit();
    audit.setId(UUID.randomUUID().toString());
    audit.setUsername(username);
    audit.setAction(action);
    audit.setTarget(target);
    audit.setCreatedAt(LocalDateTime.now());
    auditRepository.saveAndFlush(audit);
  }
}
