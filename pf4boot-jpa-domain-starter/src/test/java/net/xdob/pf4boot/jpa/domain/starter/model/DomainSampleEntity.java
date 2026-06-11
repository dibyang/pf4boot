package net.xdob.pf4boot.jpa.domain.starter.model;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * 领域 starter 集成测试实体。
 */
@Entity
public class DomainSampleEntity {

  @Id
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
