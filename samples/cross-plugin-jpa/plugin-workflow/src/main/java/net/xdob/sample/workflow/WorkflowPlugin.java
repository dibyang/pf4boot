package net.xdob.sample.workflow;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBinding;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBindingProvider;
import net.xdob.pf4boot.jpa.starter.PluginJPAStarter;
import org.pf4j.PluginWrapper;

/**
 * Sample 工作流插件。
 *
 * <p>该插件负责跨插件业务编排和审计写入，不直接访问 user-book 插件内部 Repository。</p>
 */
@PluginStarter({WorkflowStarter.class, PluginJPAStarter.class})
public class WorkflowPlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {

  public WorkflowPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo").build();
  }
}
