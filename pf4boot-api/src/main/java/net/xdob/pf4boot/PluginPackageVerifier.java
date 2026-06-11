package net.xdob.pf4boot;

import org.pf4j.PluginDescriptor;

import java.nio.file.Path;

/**
 * 插件包加载前校验扩展点。
 *
 * <p>实现类可检查插件包来源、checksum、签名或兼容性元数据。方法会在插件描述符解析后、
 * 插件 ClassLoader 创建前调用，因此实现不得依赖插件类加载器或插件 Spring 上下文。</p>
 */
public interface PluginPackageVerifier {

  /**
   * 校验待加载插件包。
   *
   * @param pluginPath 插件包路径，可能是 zip、jar 或开发目录
   * @param pluginDescriptor 已解析出的插件描述符
   * @return 校验结果，不能返回 null
   */
  PluginPackageVerificationResult verify(Path pluginPath, PluginDescriptor pluginDescriptor);
}
