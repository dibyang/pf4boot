package com.ls.plugin1;

import com.ls.plugin1.dao.entity.User;
import com.ls.plugin1.dao.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserMgrImpl
 *
 * @author yangzj
 * @version 1.0
 */
@Service
public class UserMgrImpl implements UserMgr {

  @Autowired
  private UserRepository userRepository;

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
}
