package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.PluginApplication;
import org.pf4j.Plugin;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析当前插件的 JPA 绑定配置。
 *
 * <p>解析优先级：插件级 `plugins.<plugin-id>` 配置优先，其次回退到旧的
 * `mode/domain-id` 配置，最后使用 LOCAL 默认值。</p>
 */
public class JpaPluginBindingResolver {

  private static final String PREFIX = "pf4boot.plugin.jpa";

  private JpaPluginBindingResolver() {
  }

  public static JpaPluginBinding resolve(Environment environment, BeanFactory beanFactory) {
    if (environment == null) {
      return null;
    }
    String pluginId = resolvePluginId(beanFactory);
    Map<String, Pf4bootJpaProperties.Binding> pluginBindings =
        bindPluginBindings(environment);
    if (StringUtils.hasText(pluginId) && pluginBindings.containsKey(pluginId)) {
      Pf4bootJpaProperties.Binding binding = pluginBindings.get(pluginId);
      return pluginBinding(pluginId, binding);
    }

    Pf4bootJpaProperties.Mode mode = environment.getProperty(
        PREFIX + ".mode", Pf4bootJpaProperties.Mode.class, Pf4bootJpaProperties.Mode.LOCAL);
    return new JpaPluginBinding(
        pluginId,
        mode,
        environment.getProperty(PREFIX + ".domain-id"),
        environment.getProperty(PREFIX + ".entity-manager-factory-ref"),
        environment.getProperty(PREFIX + ".transaction-manager-ref"),
        environment.getProperty(PREFIX + ".descriptor-ref"),
        additionalDomains(
            Binder.get(environment)
                .bind(PREFIX + ".additional-domains",
                    Bindable.listOf(Pf4bootJpaProperties.DomainBinding.class))
                .orElseGet(ArrayList::new)),
        false);
  }

  public static String resolvePluginId(BeanFactory beanFactory) {
    if (beanFactory == null) {
      return null;
    }
    try {
      if (beanFactory.containsBean(PluginApplication.BEAN_PLUGIN)) {
        Plugin plugin = beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Plugin.class);
        return plugin == null || plugin.getWrapper() == null ? null : plugin.getWrapper().getPluginId();
      }
    } catch (BeansException e) {
      return null;
    }
    return null;
  }

  private static Map<String, Pf4bootJpaProperties.Binding> bindPluginBindings(Environment environment) {
    Map<String, Pf4bootJpaProperties.Binding> bindings = Binder.get(environment)
        .bind(PREFIX + ".plugins", Bindable.mapOf(String.class, Pf4bootJpaProperties.Binding.class))
        .orElseGet(LinkedHashMap::new);
    return bindings == null ? new LinkedHashMap<>() : bindings;
  }

  private static JpaPluginBinding pluginBinding(
      String pluginId,
      Pf4bootJpaProperties.Binding binding) {
    if (binding == null) {
      return new JpaPluginBinding(
          pluginId, Pf4bootJpaProperties.Mode.LOCAL, null, null, null, null,
          new ArrayList<>(), true);
    }
    return new JpaPluginBinding(
        pluginId,
        binding.getMode() == null ? Pf4bootJpaProperties.Mode.LOCAL : binding.getMode(),
        binding.getDomainId(),
        binding.getEntityManagerFactoryRef(),
        binding.getTransactionManagerRef(),
        binding.getDescriptorRef(),
        additionalDomains(binding.getAdditionalDomains()),
        true);
  }

  static List<JpaDomainBinding> additionalDomains(
      List<Pf4bootJpaProperties.DomainBinding> bindings) {
    List<JpaDomainBinding> result = new ArrayList<>();
    if (bindings == null) {
      return result;
    }
    for (Pf4bootJpaProperties.DomainBinding binding : bindings) {
      if (binding == null) {
        continue;
      }
      result.add(new JpaDomainBinding(
          binding.getDomainId(),
          binding.getEntityManagerFactoryRef(),
          binding.getTransactionManagerRef(),
          binding.getDescriptorRef()));
    }
    return result;
  }
}
