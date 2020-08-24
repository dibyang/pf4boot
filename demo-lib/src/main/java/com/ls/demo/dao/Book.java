package com.ls.demo.dao;

import org.springframework.stereotype.Component;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.LocalDateTime;

/**
 * Book
 *
 * @author yangzj
 * @version 1.0
 */
@Entity
public class Book {
  @Id
  private String name;
  @Column(nullable = false)
  private String author;
  @Column(nullable = false)
  private LocalDateTime ctime;

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

  public LocalDateTime getCtime() {
    return ctime;
  }

  public void setCtime(LocalDateTime ctime) {
    this.ctime = ctime;
  }
}
