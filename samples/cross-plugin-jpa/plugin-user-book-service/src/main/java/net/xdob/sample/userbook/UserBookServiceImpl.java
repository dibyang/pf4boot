package net.xdob.sample.userbook;

import net.xdob.pf4boot.annotation.ShareService;
import net.xdob.sample.model.userbook.Book;
import net.xdob.sample.model.userbook.UserAccount;
import net.xdob.sample.userbook.repository.BookRepository;
import net.xdob.sample.userbook.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户图书业务服务实现。
 *
 * <p>所有写操作显式使用共享事务管理器，使依赖同一 domain 的插件可以参与同一事务环境。</p>
 */
@ShareService
public class UserBookServiceImpl implements UserBookService {

  @Autowired
  private UserAccountRepository userRepository;

  @Autowired
  private BookRepository bookRepository;

  @Override
  @Transactional(transactionManager = "domain.demo.transactionManager")
  public void registerUserWithBook(String username, String password, String bookName) {
    UserAccount user = new UserAccount();
    user.setUsername(username);
    user.setPassword(password);
    userRepository.saveAndFlush(user);

    Book book = new Book();
    book.setName(bookName);
    book.setAuthor(username);
    book.setCreatedAt(LocalDateTime.now());
    bookRepository.saveAndFlush(book);
  }

  @Override
  @Transactional(transactionManager = "domain.demo.transactionManager")
  public void removeUserWithBooks(String username) {
    userRepository.deleteById(username);
    bookRepository.deleteByAuthor(username);
  }

  @Override
  public List<UserAccount> listUsers() {
    return userRepository.findAll();
  }

  @Override
  public List<Book> listBooks() {
    return bookRepository.findAll();
  }
}
