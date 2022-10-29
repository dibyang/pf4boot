package net.xdob.demo.plugin2;

import net.xdob.demo.plugin1.Computer;
import org.apache.commons.text.StringSubstitutor;
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

  @RequestMapping("/info")
  public String info(){
    return StringSubstitutor.replaceSystemProperties(
        "You are running with java.version = ${java.version} and os.name = ${os.name}.");
  }
}
