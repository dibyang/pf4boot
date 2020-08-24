package com.ls.plugin1;

import com.ls.demo.dao.Book;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TestController
 *
 * @author yangzj
 * @version 1.0
 */
@RestController
@RequestMapping("/api/book")
public class BookMgrController {

  @Autowired
  private BookMgr bookMgr;

  @RequestMapping("/list")
  public List<Book> list(){
    return bookMgr.getAllBooks();
  }

  @RequestMapping("/add")
  public List<Book> add(String name, String author){
    bookMgr.addBook(name,author);
    return bookMgr.getAllBooks();
  }

  @RequestMapping("/remove")
  public List<Book> remove(String name){
    bookMgr.removeBook(name);
    return bookMgr.getAllBooks();
  }

}
