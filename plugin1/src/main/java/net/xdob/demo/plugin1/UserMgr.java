package net.xdob.demo.plugin1;

import net.xdob.demo.plugin1.dao.entity.User;

import java.util.List;

/**
 * UserMgr
 *
 * @author yangzj
 * @version 1.0
 */
public interface UserMgr {
  List<User> getAllUsers();
  void addUser(String username, String password);
  void removeUser(String username);
  void removeUserAndBooks(String username);
}
