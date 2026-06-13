package net.xdob.sample.unrelated;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 无 JPA 依赖的运行态隔离检查接口。
 */
@RestController
@RequestMapping("/api/sample/unrelated")
public class UnrelatedController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("pluginId", "sample-unrelated-service");
    result.put("jpa", false);
    result.put("status", "UP");
    return result;
  }
}
