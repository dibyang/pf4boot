package net.xdob.sample.userbook;

import net.xdob.sample.model.userbook.Book;
import net.xdob.sample.model.userbook.UserAccount;

import java.util.List;

/**
 * 用户图书业务服务。
 *
 * <p>作为跨插件导出的服务接口，workflow 插件通过该接口组合业务，不直接访问本插件 Repository。</p>
 */
public interface UserBookService {

  void registerUserWithBook(String username, String password, String bookName);

  void removeUserWithBooks(String username);

  List<UserAccount> listUsers();

  List<Book> listBooks();
}
