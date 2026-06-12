package net.xdob.pf4boot.capability;

import java.util.List;

/**
 * 插件能力声明描述符。
 *
 * <p>描述符聚合一个插件提供和依赖的能力。它只用于预检和诊断，不参与 PF4J 的
 * dependency resolver。</p>
 */
public interface PluginCapabilityDescriptor {

  String getPluginId();

  List<PluginCapability> provides();

  List<PluginCapabilityRequirement> requires();
}
