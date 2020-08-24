package com.ls.plugin1;

import com.ls.plugin1.dao.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UserMgrController
 *
 * @author yangzj
 * @version 1.0
 */
@RestController
@RequestMapping("/api/user")
public class UserMgrController {

  @Autowired
  private UserMgr userMgr;

  @RequestMapping("/list")
  public List<User> list(){
    return userMgr.getAllUsers();
  }

}
