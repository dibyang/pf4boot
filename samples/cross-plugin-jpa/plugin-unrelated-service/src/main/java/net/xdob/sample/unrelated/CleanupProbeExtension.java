package net.xdob.sample.unrelated;

import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.beans.factory.DisposableBean;
import java.net.ServerSocket;

/** 不经 Spring 扫描的 PF4J 扩展，smoke 验证上下文拥有其销毁责任。 */
@Extension
public class CleanupProbeExtension implements ExtensionPoint, DisposableBean {
  private final ServerSocket socket;
  public CleanupProbeExtension() throws Exception {
    socket = Boolean.getBoolean("sample.cleanup-probe-enabled")
        ? new ServerSocket(Integer.getInteger("sample.extension.port", 0)) : null;
    if (socket != null) { System.setProperty("sample.extension.port", String.valueOf(socket.getLocalPort())); }
  }
  @Override
  public void destroy() throws Exception {
    if (socket == null) { return; }
    if (Boolean.getBoolean("sample.extension.fail-close")) { throw new IllegalStateException("extension close failure"); }
    if (socket.isClosed()) { throw new IllegalStateException("duplicate extension destroy"); }
    socket.close();
    System.setProperty("sample.extension.destroys", String.valueOf(Integer.getInteger("sample.extension.destroys", 0) + 1));
  }
}
