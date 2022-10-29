package net.xdob.demo.plugin1;

import net.xdob.pf4boot.annotation.ShareService;
import net.xdob.demo.dao.Book;
import net.xdob.demo.dao.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BookMgrImpl
 *
 * @author yangzj
 * @version 1.0
 */
@ShareService
public class BookMgrImpl implements BookMgr {
  @Autowired
  private BookRepository bookRepository;


  @Override
  public List<Book> getAllBooks() {
    return bookRepository.findAll();
  }

  @Override
  public void addBook(String name, String author) {
    Book book = new Book();
    book.setName(name);
    book.setAuthor(author);
    book.setCtime(LocalDateTime.now());
    bookRepository.saveAndFlush(book);
  }

  @Override
  public void removeBook(String name) {
    bookRepository.deleteById(name);
  }
}
