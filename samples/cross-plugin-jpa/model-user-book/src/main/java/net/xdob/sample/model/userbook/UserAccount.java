package net.xdob.sample.model.userbook;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Sample 用户实体。
 *
 * <p>实体属于独立 model 模块，由 JPA domain provider 扫描；业务插件只定义 Repository 和服务。</p>
 */
@Entity
@Table(name = "sample_user_account")
public class UserAccount {
  @Id
  private String username;

  @Column(nullable = false)
  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
