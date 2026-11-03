package net.xdob.sample.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供 smoke 验证另一个 zip 插件内的真实 Servlet 分派。 */
@RestController
@ConditionalOnProperty(name = "sample.cleanup-probe-enabled", havingValue = "true")
public class DispatchProbeController {
  @GetMapping("/api/sample/workflow/dispatch-target")
  public String target() throws Exception {
    System.setProperty("sample.dispatch.inner-entered", "true");
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (Boolean.getBoolean("sample.dispatch.inner-block")) {
      if (System.nanoTime() >= deadline) { throw new IllegalStateException("dispatch fixture timeout"); }
      Thread.sleep(10);
    }
    return "target-ok";
  }
}
