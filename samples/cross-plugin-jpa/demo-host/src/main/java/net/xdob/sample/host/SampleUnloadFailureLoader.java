package net.xdob.sample.host;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.internal.Pf4bootPluginClassLoader;
import net.xdob.pf4boot.loader.ZipPf4bootPluginLoader;
import org.pf4j.*;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/** smoke 通过已有 custom-plugin-loader 配置验证真实 zip loader 部分关闭后失败。 */
public class SampleUnloadFailureLoader extends ZipPf4bootPluginLoader {
  public SampleUnloadFailureLoader(PluginManager manager) { super((Pf4bootPluginManager) manager); }
  @Override
  public ClassLoader loadPlugin(Path path, PluginDescriptor descriptor) {
    try (PluginClassLoader original = (PluginClassLoader) super.loadPlugin(path, descriptor)) {
      Pf4bootPluginClassLoader loader = new Pf4bootPluginClassLoader(pluginManager, descriptor) {
        @Override public void close() throws IOException {
          super.close();
          if ("sample-unrelated-service".equals(descriptor.getPluginId()) && Boolean.getBoolean("sample.unload.fail-loader")) {
            throw new IllegalStateException("smoke partial loader close failure");
          }
        }
      };
      for (URL url : original.getURLs()) { loader.addURL(url); }
      return loader;
    } catch (IOException e) { throw new IllegalStateException(e); }
  }
}
