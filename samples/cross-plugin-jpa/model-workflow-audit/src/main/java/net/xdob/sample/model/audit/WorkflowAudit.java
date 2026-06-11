package net.xdob.sample.model.audit;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Sample 工作流审计实体。
 *
 * <p>实体放在独立 model 模块，provider 负责扫描，workflow 插件负责 Repository 和写入逻辑。</p>
 */
@Entity
@Table(name = "sample_workflow_audit")
public class WorkflowAudit {
  @Id
  private String id;

  @Column(nullable = false)
  private String username;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String target;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
