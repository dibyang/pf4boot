package net.xdob.pf4boot.capability;

import net.xdob.pf4boot.PluginPackageVerificationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 插件能力需求预检器。
 *
 * <p>预检器只做部署前诊断，不参与 PF4J dependency 解析。第一阶段按能力名和 requirement
 * 中声明的 attributes 做精确匹配；versionRange 暂时仅保留在诊断消息中。</p>
 */
public class PluginCapabilityPrecheck {

  public List<PluginCapabilityPrecheckResult> check(
      PluginCapabilityDescriptor descriptor,
      List<PluginCapability> availableCapabilities,
      PluginPackageVerificationMode mode) {
    List<PluginCapabilityPrecheckResult> results = new ArrayList<>();
    if (mode == null || PluginPackageVerificationMode.DISABLED.equals(mode)) {
      return results;
    }
    if (descriptor == null || descriptor.requires() == null || descriptor.requires().isEmpty()) {
      return results;
    }
    List<PluginCapability> available = availableCapabilities == null
        ? new ArrayList<PluginCapability>()
        : availableCapabilities;
    for (PluginCapabilityRequirement requirement : descriptor.requires()) {
      if (requirement == null || !requirement.isRequired()) {
        continue;
      }
      if (!matchesAny(requirement, available)) {
        results.add(result(mode, "PFC-002",
            "Required capability missing: " + requirement.getName()
                + attributesMessage(requirement)));
      }
    }
    if (results.isEmpty()) {
      results.add(PluginCapabilityPrecheckResult.ok("Plugin capability requirements are satisfied"));
    }
    return results;
  }

  private boolean matchesAny(PluginCapabilityRequirement requirement, List<PluginCapability> capabilities) {
    for (PluginCapability capability : capabilities) {
      if (matches(requirement, capability)) {
        return true;
      }
    }
    return false;
  }

  private boolean matches(PluginCapabilityRequirement requirement, PluginCapability capability) {
    if (capability == null || isBlank(requirement.getName())) {
      return false;
    }
    if (!requirement.getName().equals(capability.getName())) {
      return false;
    }
    for (Map.Entry<String, String> entry : requirement.getAttributes().entrySet()) {
      if (isConsumerOwnedAttribute(requirement, entry.getKey())) {
        continue;
      }
      String expected = entry.getValue();
      if (isBlank(expected)) {
        continue;
      }
      String actual = capability.getAttributes().get(entry.getKey());
      if (!expected.equals(actual)) {
        return false;
      }
    }
    return true;
  }

  private boolean isConsumerOwnedAttribute(PluginCapabilityRequirement requirement, String attributeName) {
    if (!"jpa.datasource".equals(requirement.getName())) {
      return false;
    }
    return "entityPackages".equals(attributeName) || "repositoryPackages".equals(attributeName);
  }

  private PluginCapabilityPrecheckResult result(
      PluginPackageVerificationMode mode,
      String code,
      String message) {
    if (PluginPackageVerificationMode.ENFORCE.equals(mode)) {
      return PluginCapabilityPrecheckResult.fail(code, message);
    }
    return PluginCapabilityPrecheckResult.warn(code, message);
  }

  private String attributesMessage(PluginCapabilityRequirement requirement) {
    if (requirement.getAttributes().isEmpty()) {
      return "";
    }
    return " attributes=" + requirement.getAttributes();
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
