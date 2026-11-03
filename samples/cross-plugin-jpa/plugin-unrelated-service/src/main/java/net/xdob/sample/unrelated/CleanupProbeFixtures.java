package net.xdob.sample.unrelated;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;
import java.net.ServerSocket;
import java.util.concurrent.Callable;

/** 仅由真实宿主 smoke 启用的异步、拦截器和端口资源夹具。 */
@Configuration
@ConditionalOnProperty(name = "sample.cleanup-probe-enabled", havingValue = "true")
public class CleanupProbeFixtures {
  @Bean public ProbeSocket probeSocket() throws Exception { return new ProbeSocket(); }
  @Bean public MappedInterceptor cleanupProbeInterceptor() {
    return new MappedInterceptor(new String[]{"/api/sample/lifecycle-host-block"}, new HandlerInterceptor() {
      public boolean preHandle(javax.servlet.http.HttpServletRequest request,
          javax.servlet.http.HttpServletResponse response, Object handler) throws Exception {
        System.setProperty("sample.cleanup.interceptor-entered", "true");
        waitForRelease("sample.cleanup.interceptor-block");
        return true;
      }
    });
  }
  public static class ProbeSocket implements AutoCloseable {
    private final ServerSocket socket;
    public ProbeSocket() throws Exception {
      socket = new ServerSocket(Integer.getInteger("sample.cleanup.socket-port", 0));
      System.setProperty("sample.cleanup.socket-port", String.valueOf(socket.getLocalPort()));
    }
    public void close() throws Exception {
      if (Boolean.getBoolean("sample.cleanup.fail-close")) { throw new IllegalStateException("smoke socket close failure"); }
      socket.close();
    }
  }
  @RestController
  @ConditionalOnProperty(name = "sample.cleanup-probe-enabled", havingValue = "true")
  public static class ProbeController {
    @GetMapping("/api/sample/unrelated/async")
    public Callable<String> async() { return () -> "async-ok"; }
    @GetMapping("/api/sample/unrelated/deferred")
    public DeferredResult<String> deferred() {
      DeferredResult<String> result = new DeferredResult<>(); result.setResult("deferred-ok"); return result;
    }
    @GetMapping("/api/sample/unrelated/async-timeout")
    public WebAsyncTask<String> timeout() {
      WebAsyncTask<String> task = new WebAsyncTask<>(50L, () -> {
        System.setProperty("sample.cleanup.async-entered", "true");
        waitForRelease("sample.cleanup.async-block");
        return "worker-finished";
      });
      task.onTimeout(() -> "timed-out");
      return task;
    }
  }
  private static void waitForRelease(String property) throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (Boolean.getBoolean(property)) {
      if (System.nanoTime() >= deadline) { throw new IllegalStateException("smoke release timeout"); }
      // 故意模拟忽略取消的用户工作，用于验证响应结束不等于工作已退出。
      try { Thread.sleep(10); } catch (InterruptedException ignored) { }
    }
  }
}
