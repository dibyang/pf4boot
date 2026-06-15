package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.starter.JpaDomainBinding;
import net.xdob.pf4boot.jpa.starter.JpaPluginBinding;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认共享 JPA consumer 绑定注册表。
 */
public class DefaultJpaPluginBindingRegistry implements JpaPluginBindingRegistry {

  private final ConcurrentHashMap<String, JpaPluginBinding> bindings = new ConcurrentHashMap<>();

  @Override
  public void register(JpaPluginBinding binding) {
    if (binding == null || !StringUtils.hasText(binding.getPluginId())) {
      return;
    }
    bindings.put(binding.getPluginId(), binding);
  }

  @Override
  public void remove(String pluginId) {
    if (StringUtils.hasText(pluginId)) {
      bindings.remove(pluginId);
    }
  }

  @Override
  public JpaPluginBinding findByPluginId(String pluginId) {
    return StringUtils.hasText(pluginId) ? bindings.get(pluginId) : null;
  }

  @Override
  public List<JpaPluginBinding> findByDomainId(String domainId) {
    if (!StringUtils.hasText(domainId)) {
      return Collections.emptyList();
    }
    List<JpaPluginBinding> result = new ArrayList<>();
    for (JpaPluginBinding binding : bindings.values()) {
      if (matchesDomain(binding, domainId)) {
        result.add(binding);
      }
    }
    result.sort((left, right) -> safe(left.getPluginId()).compareToIgnoreCase(safe(right.getPluginId())));
    return Collections.unmodifiableList(result);
  }

  @Override
  public Map<String, JpaPluginBinding> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
  }

  private boolean matchesDomain(JpaPluginBinding binding, String domainId) {
    if (binding == null || !binding.isShared()) {
      return false;
    }
    if (domainId.equals(binding.getDomainId())) {
      return true;
    }
    for (JpaDomainBinding additional : binding.getAdditionalDomains()) {
      if (domainId.equals(additional.getDomainId())) {
        return true;
      }
    }
    return false;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
