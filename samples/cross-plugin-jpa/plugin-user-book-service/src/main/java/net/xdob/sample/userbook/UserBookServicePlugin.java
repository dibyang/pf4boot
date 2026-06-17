package net.xdob.sample.userbook;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBinding;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBindingProvider;
import net.xdob.pf4boot.jpa.starter.PluginJPAStarter;
import org.pf4j.PluginWrapper;

/**
 * Sample 用户图书业务插件。
 *
 * <p>该插件依赖共享 JPA domain，自己只定义 Repository 和业务服务。</p>
 */
@PluginStarter({UserBookServiceStarter.class, PluginJPAStarter.class})
public class UserBookServicePlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {

  public UserBookServicePlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo").build();
  }
}
