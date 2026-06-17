package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.jpa.binding.JpaBindingMode;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBinding;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBindingProvider;
import net.xdob.pf4boot.jpa.binding.JpaConsumerDomainBinding;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
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

  private static final Logger LOG = LoggerFactory.getLogger(JpaPluginBindingResolver.class);

  private static final String PREFIX = "pf4boot.plugin.jpa";

  private JpaPluginBindingResolver() {
  }

  public static JpaPluginBinding resolve(Environment environment, BeanFactory beanFactory) {
    String pluginId = resolvePluginId(beanFactory);
    JpaConsumerBinding beanBinding = resolveBeanBinding(beanFactory);
    if (beanBinding != null) {
      return fromConsumerBinding(pluginId, beanBinding);
    }
    JpaConsumerBinding providerBinding = resolveProviderBinding(beanFactory);
    if (providerBinding != null) {
      return fromConsumerBinding(pluginId, providerBinding);
    }
    if (environment == null) {
      return new JpaPluginBinding(
          pluginId, Pf4bootJpaProperties.Mode.LOCAL, null, null, null, null,
          new ArrayList<>(), true);
    }
    Map<String, Pf4bootJpaProperties.Binding> pluginBindings =
        bindPluginBindings(environment);
    if (StringUtils.hasText(pluginId) && pluginBindings.containsKey(pluginId)) {
      warnLegacy("pf4boot.plugin.jpa.plugins." + pluginId + ".*");
      Pf4bootJpaProperties.Binding binding = pluginBindings.get(pluginId);
      return pluginBinding(pluginId, binding);
    }

    if (hasLegacyGlobalBinding(environment)) {
      warnLegacy("pf4boot.plugin.jpa.mode/domain-id");
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

  private static JpaConsumerBinding resolveBeanBinding(BeanFactory beanFactory) {
    if (!(beanFactory instanceof ListableBeanFactory)) {
      return null;
    }
    Map<String, JpaConsumerBinding> bindings =
        ((ListableBeanFactory) beanFactory).getBeansOfType(JpaConsumerBinding.class);
    if (bindings == null || bindings.isEmpty()) {
      return null;
    }
    if (bindings.size() > 1) {
      throw new IllegalStateException(
          "[PJF-006] Plugin must expose only one JpaConsumerBinding bean.");
    }
    return bindings.values().iterator().next();
  }

  private static JpaConsumerBinding resolveProviderBinding(BeanFactory beanFactory) {
    if (beanFactory == null) {
      return null;
    }
    try {
      if (!beanFactory.containsBean(PluginApplication.BEAN_PLUGIN)) {
        return null;
      }
      Object plugin = beanFactory.getBean(PluginApplication.BEAN_PLUGIN);
      if (plugin instanceof JpaConsumerBindingProvider) {
        return ((JpaConsumerBindingProvider) plugin).jpaConsumerBinding();
      }
    } catch (BeansException e) {
      LOG.debug("[PF4BOOT-JPA] cannot resolve JpaConsumerBindingProvider from plugin bean", e);
    }
    return null;
  }

  private static JpaPluginBinding fromConsumerBinding(String pluginId, JpaConsumerBinding binding) {
    if (binding == null || !binding.isShared()) {
      return new JpaPluginBinding(
          pluginId, Pf4bootJpaProperties.Mode.LOCAL, null, null, null, null,
          new ArrayList<>(), true);
    }
    JpaConsumerDomainBinding primary = binding.getPrimaryDomain();
    if (primary == null) {
      return new JpaPluginBinding(
          pluginId, Pf4bootJpaProperties.Mode.SHARED, null, null, null, null,
          new ArrayList<>(), true);
    }
    return new JpaPluginBinding(
        pluginId,
        JpaBindingMode.SHARED == binding.getMode()
            ? Pf4bootJpaProperties.Mode.SHARED
            : Pf4bootJpaProperties.Mode.LOCAL,
        primary.getDomainId(),
        primary.getEntityManagerFactoryRef(),
        primary.getTransactionManagerRef(),
        primary.getDescriptorRef(),
        fromConsumerDomains(binding.getAdditionalDomains()),
        true);
  }

  private static List<JpaDomainBinding> fromConsumerDomains(List<JpaConsumerDomainBinding> bindings) {
    List<JpaDomainBinding> result = new ArrayList<>();
    if (bindings == null) {
      return result;
    }
    for (JpaConsumerDomainBinding binding : bindings) {
      if (binding != null) {
        result.add(new JpaDomainBinding(
            binding.getDomainId(),
            binding.getEntityManagerFactoryRef(),
            binding.getTransactionManagerRef(),
            binding.getDescriptorRef()));
      }
    }
    return result;
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

  private static boolean hasLegacyGlobalBinding(Environment environment) {
    return environment != null
        && (StringUtils.hasText(environment.getProperty(PREFIX + ".mode"))
        || StringUtils.hasText(environment.getProperty(PREFIX + ".domain-id"))
        || StringUtils.hasText(environment.getProperty(PREFIX + ".entity-manager-factory-ref"))
        || StringUtils.hasText(environment.getProperty(PREFIX + ".transaction-manager-ref"))
        || StringUtils.hasText(environment.getProperty(PREFIX + ".descriptor-ref")));
  }

  private static void warnLegacy(String key) {
    LOG.warn("[PF4BOOT-JPA] Deprecated consumer JPA configuration '{}' is used as compatibility fallback. "
        + "Plugin JPA binding should be declared by the consumer plugin via JpaConsumerBinding.", key);
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
