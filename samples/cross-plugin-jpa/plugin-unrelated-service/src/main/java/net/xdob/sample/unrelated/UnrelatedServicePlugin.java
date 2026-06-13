package net.xdob.sample.unrelated;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.PluginWrapper;

/**
 * 无 JPA 依赖的无关插件。
 */
@PluginStarter(UnrelatedServiceStarter.class)
public class UnrelatedServicePlugin extends Pf4bootPlugin {

  public UnrelatedServicePlugin(PluginWrapper wrapper) {
    super(wrapper);
  }
}
