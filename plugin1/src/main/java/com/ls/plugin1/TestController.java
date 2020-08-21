package com.ls.plugin1;

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
public class TestController {

  @RequestMapping("/hello1")
  public String hello1(String name){
    return "hello1 "+name+"!";
  }
}
