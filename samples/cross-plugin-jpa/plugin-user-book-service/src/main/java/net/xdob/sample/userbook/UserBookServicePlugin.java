package net.xdob.sample.userbook;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.starter.PluginJPAStarter;
import org.pf4j.PluginWrapper;

/**
 * Sample 用户图书业务插件。
 *
 * <p>该插件依赖共享 JPA domain，自己只定义 Repository 和业务服务。</p>
 */
@PluginStarter({UserBookServiceStarter.class, PluginJPAStarter.class})
public class UserBookServicePlugin extends Pf4bootPlugin {

  public UserBookServicePlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void initiate() {
    setProperty("pf4boot.plugin.jpa.enabled", "true");
    setProperty("pf4boot.plugin.jpa.plugins.sample-user-book-service.mode", "SHARED");
    setProperty("pf4boot.plugin.jpa.plugins.sample-user-book-service.domain-id", "demo");
    setProperty("pf4boot.plugin.jpa.mode", "SHARED");
    setProperty("pf4boot.plugin.jpa.domain-id", "demo");
  }
}
