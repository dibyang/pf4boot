package com.ls.plugin1.dao.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.LocalDateTime;

/**
 * User
 *
 * @author yangzj
 * @version 1.0
 */
@Entity
public class User {
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
