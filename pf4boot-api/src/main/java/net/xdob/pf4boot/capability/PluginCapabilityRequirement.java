package net.xdob.pf4boot.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件消费方声明的能力需求。
 *
 * <p>需求用于部署前判断宿主或已启动插件是否提供了对应能力。第一阶段只支持按能力名和
 * 属性精确匹配，版本范围作为诊断字段保留。</p>
 */
public class PluginCapabilityRequirement {

  private String name;
  private String versionRange;
  private boolean required = true;
  private Map<String, String> attributes = new LinkedHashMap<>();

  public PluginCapabilityRequirement() {
  }

  public PluginCapabilityRequirement(
      String name,
      String versionRange,
      boolean required,
      Map<String, String> attributes) {
    this.name = name;
    this.versionRange = versionRange;
    this.required = required;
    setAttributes(attributes);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersionRange() {
    return versionRange;
  }

  public void setVersionRange(String versionRange) {
    this.versionRange = versionRange;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
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
