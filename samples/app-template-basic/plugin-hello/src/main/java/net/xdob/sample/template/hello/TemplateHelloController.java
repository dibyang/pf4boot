package net.xdob.sample.template.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模板插件的最小 Web 接口。
 */
@RestController
@RequestMapping("/api/template")
public class TemplateHelloController {

  @GetMapping("/hello")
  public Map<String, Object> hello() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("pluginId", "template-hello-plugin");
    result.put("message", "hello from pf4boot plugin");
    result.put("status", "UP");
    return result;
  }
}

