package net.xdob.pf4boot.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件提供的运行时能力声明。
 *
 * <p>能力声明用于部署前预检和诊断，不替代 PF4J 插件依赖。典型能力包括
 * {@code web.mvc}、{@code jpa.datasource}、{@code jpa.consumer} 等。</p>
 */
public class PluginCapability {

  private String name;
  private String version;
  private String scope;
  private Map<String, String> attributes = new LinkedHashMap<>();

  public PluginCapability() {
  }

  public PluginCapability(String name, String version, String scope, Map<String, String> attributes) {
    this.name = name;
    this.version = version;
    this.scope = scope;
    setAttributes(attributes);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public Map<String, String> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes == null
        ? new LinkedHashMap<String, String>()
        : new LinkedHashMap<>(attributes);
  }
}
