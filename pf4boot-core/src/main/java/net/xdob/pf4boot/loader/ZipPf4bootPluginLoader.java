package net.xdob.pf4boot.loader;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.internal.Pf4bootPluginClassLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginLoader;
import org.pf4j.util.FileUtils;
import org.pf4j.util.JarFileFilter;
import org.pf4j.util.Unzip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * ZipPf4bootPluginLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class ZipPf4bootPluginLoader implements PluginLoader {
  static final Logger log = LoggerFactory.getLogger(ZipPf4bootPluginLoader.class);
  public static final String PLUGIN_CACHE = "plugin-cache";
  protected Pf4bootPluginManager pluginManager;

  public ZipPf4bootPluginLoader(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public boolean isApplicable(Path pluginPath) {
    return Files.exists(pluginPath) && FileUtils.isZipFile(pluginPath);
  }

  @Override
  public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
    PluginClassLoader pluginClassLoader = new Pf4bootPluginClassLoader(pluginManager, pluginDescriptor);
    Path cache = pluginManager.getPluginCacheDir();
    if(FileUtils.isZipFile(pluginPath)){
      try {
        String fileName = pluginPath.getFileName().toString();
        String directoryName = fileName.substring(0, fileName.lastIndexOf("."));
        Path pluginDirectory = cache.resolve(directoryName);

        expandIfZip(pluginPath, pluginDirectory);
        File[] libs = pluginDirectory.resolve("lib").toFile().listFiles(new JarFileFilter());
        for (File lib : libs) {
          pluginClassLoader.addFile(lib);
        }
      } catch (IOException e) {
        log.error("Cannot expand plugin zip '{}'", pluginPath);
        log.error(e.getMessage(), e);
      }
    }
    return pluginClassLoader;
  }

  public static Path expandIfZip(Path filePath, Path pluginDirectory) throws IOException {
    if (!FileUtils.isZipFile(filePath)) {
      return filePath;
    }

    FileTime pluginZipDate = Files.getLastModifiedTime(filePath);

    if (!Files.exists(pluginDirectory) || pluginZipDate.compareTo(Files.getLastModifiedTime(pluginDirectory)) > 0) {
      // expand '.zip' file
      Unzip unzip = new Unzip();
      unzip.setSource(filePath.toFile());
      unzip.setDestination(pluginDirectory.toFile());
      unzip.extract();
      log.info("Expanded plugin zip '{}' in '{}'", filePath.getFileName(), pluginDirectory.getFileName());
    }

    return pluginDirectory;
  }

}
