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

  public UnrelatedServicePlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void initiate() { probeDestroyed = false; }

  @Override
  public void start() {
    if (Boolean.getBoolean("sample.cleanup-probe-enabled")) {
      try {
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
    if (probeDestroyed) { throw new IllegalStateException("duplicate plugin destroy"); }
    if (probeSocket != null) { probeSocket.close(); probeSocket = null; }
    probeDestroyed = true;
    int count = Integer.getInteger("sample.dispatch.plugin-destroys", 0);
    System.setProperty("sample.dispatch.plugin-destroys", String.valueOf(count + 1));
  }
}
