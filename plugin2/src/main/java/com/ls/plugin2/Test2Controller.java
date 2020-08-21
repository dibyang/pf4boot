package com.ls.plugin2;

import com.ls.plugin1.Computer;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired
  private Computer computer;


  @RequestMapping("/add")
  public double add(double n1, double n2){
    return computer.add(n1,n2);
  }
}
