package net.xdob.sample.unrelated;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.DisposableBean;

/**
 * 无 JPA 依赖的无关插件。
 */
@PluginStarter(UnrelatedServiceStarter.class)
public class UnrelatedServicePlugin extends Pf4bootPlugin implements DisposableBean {
  private java.net.ServerSocket probeSocket;
  private boolean probeDestroyed;
  private java.net.ServerSocket startupSocket;

  public UnrelatedServicePlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void initiate() {
    probeDestroyed = false;
    if (Boolean.getBoolean("sample.startup-drain.fail")) {
      System.setProperty("sample.startup-drain.attempts", String.valueOf(Integer.getInteger("sample.startup-drain.attempts", 0) + 1));
      try {
        startupSocket = new java.net.ServerSocket(Integer.getInteger("sample.startup-drain.port", 0));
        System.setProperty("sample.startup-drain.port", String.valueOf(startupSocket.getLocalPort()));
      } catch (java.io.IOException e) { throw new IllegalStateException(e); }
      java.util.function.Consumer work = getPluginManager().getRootContext().getBean("sampleEarlyStartupWork", java.util.function.Consumer.class);
      work.accept(this);
      throw new IllegalStateException("smoke initiate failure with in-flight work");
    }
  }

  /** initiate 创建的资源不能依赖尚未创建的 Spring 上下文关闭。 */
  @Override
  public void stop() {
    if (startupSocket != null) {
      try { startupSocket.close(); startupSocket = null; }
      catch (java.io.IOException e) { throw new IllegalStateException(e); }
      System.setProperty("sample.startup-drain.stops", String.valueOf(Integer.getInteger("sample.startup-drain.stops", 0) + 1));
    }
  }

  /** smoke 模拟上下文已创建但尚未 refresh 就失败的资源释放。 */
  @Override
  protected org.springframework.context.ConfigurableApplicationContext createPluginContext(
      org.springframework.context.ConfigurableApplicationContext parent) {
    org.springframework.context.ConfigurableApplicationContext context = super.createPluginContext(parent);
    if (Boolean.getBoolean("sample.cleanup-probe-enabled") && Boolean.getBoolean("sample.pre-refresh.fail")) {
      try {
        probeSocket = new java.net.ServerSocket(Integer.getInteger("sample.pre-refresh.port", 0));
        System.setProperty("sample.pre-refresh.port", String.valueOf(probeSocket.getLocalPort()));
      } catch (java.io.IOException e) { throw new IllegalStateException(e); }
      throw new IllegalStateException("smoke failure before refresh");
    }
    return context;
  }

  @Override
  public void start() {
    if (Boolean.getBoolean("sample.cleanup-probe-enabled")) {
      // 扩展稍后重绑自己的测试端口，随机分配不能先占用该端口。
      try (java.net.ServerSocket reserved = Integer.getInteger("sample.extension.port", 0) > 0
          ? new java.net.ServerSocket(Integer.getInteger("sample.extension.port")) : null) {
        probeSocket = new java.net.ServerSocket(0);
        System.setProperty("sample.dispatch.plugin-port", String.valueOf(probeSocket.getLocalPort()));
      } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
  }

  /** smoke 使用真实端口，并故意拒绝同一个销毁动作被调用两次。 */
  @javax.annotation.PreDestroy
  @Override
  public void destroy() throws Exception {
    if (!Boolean.getBoolean("sample.cleanup-probe-enabled")) { return; }
    if (Boolean.getBoolean("sample.pre-refresh.fail-destroy")) { throw new java.io.IOException("smoke pre-refresh destruction failure"); }
    if (probeDestroyed) { throw new IllegalStateException("duplicate plugin destroy"); }
    if (probeSocket != null) { probeSocket.close(); probeSocket = null; }
    probeDestroyed = true;
    int count = Integer.getInteger("sample.dispatch.plugin-destroys", 0);
    System.setProperty("sample.dispatch.plugin-destroys", String.valueOf(count + 1));
  }
}
