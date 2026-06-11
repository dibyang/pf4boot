package net.xdob.sample.model.userbook;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Sample 图书实体。
 *
 * <p>与 {@link UserAccount} 一起放在 model 模块，避免数据源能力插件混入业务模型。</p>
 */
@Entity
@Table(name = "sample_book")
public class Book {
  @Id
  private String name;

  @Column(nullable = false)
  private String author;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
