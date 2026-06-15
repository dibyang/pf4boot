package net.xdob.sample.host;

import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collection;

/**
 * Sample 专用 JPA reload drain 验证器。
 *
 * <p>默认不阻断 drain；runtime smoke 在 user.home 下创建标记文件时，awaitDrain 返回 false，
 * 用于稳定验证 drain timeout 不会停止插件。</p>
 */
@Component
public class SampleJpaReloadTestDrainer implements PluginTrafficDrainer {

  private static final String TIMEOUT_MARKER = "jpa-drain-timeout.flag";

  @Override
  public void beginDrain(Collection<String> pluginIds) {
    // Sample drainer 只用于 smoke 注入，不维护额外状态。
  }

  @Override
  public boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) {
    return !new File(System.getProperty("user.home"), TIMEOUT_MARKER).exists();
  }

  @Override
  public void endDrain(Collection<String> pluginIds) {
    // Sample drainer 只用于 smoke 注入，不维护额外状态。
  }
}
