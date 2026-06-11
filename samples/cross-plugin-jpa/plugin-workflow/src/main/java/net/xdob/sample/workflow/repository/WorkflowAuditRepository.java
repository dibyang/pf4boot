package net.xdob.sample.workflow.repository;

import net.xdob.sample.model.audit.WorkflowAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工作流审计 Repository。
 */
@Repository
public interface WorkflowAuditRepository extends JpaRepository<WorkflowAudit, String> {

  List<WorkflowAudit> findByUsername(String username);
}
