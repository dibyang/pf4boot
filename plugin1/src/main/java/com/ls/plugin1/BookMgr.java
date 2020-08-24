package com.ls.plugin1;

import com.ls.demo.dao.Book;

import java.util.List;

/**
 * BookMgr
 *
 * @author yangzj
 * @version 1.0
 */
public interface BookMgr {
  List<Book> getAllBooks();
  void addBook(String name, String author);
  void removeBook(String name);
}
