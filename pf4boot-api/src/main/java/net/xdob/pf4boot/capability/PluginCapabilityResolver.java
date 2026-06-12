package net.xdob.pf4boot.capability;

import org.pf4j.PluginDescriptor;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * 插件能力声明解析器。
 *
 * <p>解析器负责从插件包旁路 manifest 或其他宿主约定来源读取能力声明。返回结果只供预检
 * 使用，不改变插件加载状态。</p>
 */
public interface PluginCapabilityResolver {

  PluginCapabilityDescriptor resolve(Path pluginPath, PluginDescriptor pluginDescriptor);

  List<PluginCapability> providedCapabilities(Collection<PluginCapabilityDescriptor> descriptors);
}
