package com.ls.plugin2;

import com.ls.pf4boot.Computer;
import com.ls.plugin1.Computer2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TestController
 *
 * @author yangzj
 * @version 1.0
 */
@RestController
@RequestMapping("/api")
public class Test2Controller {

  @Qualifier("computer")
  @Autowired
  private Computer computer;

  @Autowired
  private Computer2 computer2;

  @RequestMapping("/add")
  public double add(double n1, double n2){
    return computer.add(n1,n2);
  }

  @RequestMapping("/add2")
  public double add2(double n1, double n2){
    return computer2.add2(n1,n2);
  }
}
