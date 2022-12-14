package net.xdob.demo.plugin1;

import net.xdob.demo.dao.BookRepository;
import net.xdob.pf4boot.annotation.Export;
import net.xdob.demo.plugin1.dao.entity.User;
import net.xdob.demo.plugin1.dao.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * UserMgrImpl
 *
 * @author yangzj
 * @version 1.0
 */
@Service
@Export
public class UserMgrImpl implements UserMgr {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BookRepository bookRepository;

  @Override
  public List<User> getAllUsers() {
    return userRepository.findAll();
  }

  @Override
  public void addUser(String username, String password) {
    User user = new User();
    user.setUsername(username);
    user.setPassword(password);
    userRepository.saveAndFlush(user);
  }


  @Override
  public void removeUser(String username) {
    userRepository.deleteById(username);
  }

  @Override
  @Transactional
  public void removeUserAndBooks(String username) {
    userRepository.deleteById(username);
    bookRepository.deleteByAuthor(username);
  }
}
