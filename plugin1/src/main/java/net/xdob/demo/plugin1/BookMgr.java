package net.xdob.demo.plugin1;

import net.xdob.demo.dao.Book;

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
