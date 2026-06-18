package net.xdob.sample.template.hello;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.PluginWrapper;

/**
 * 最小 hello 插件入口。
 */
@PluginStarter(TemplateHelloStarter.class)
public class TemplateHelloPlugin extends Pf4bootPlugin {

  public TemplateHelloPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }
}

