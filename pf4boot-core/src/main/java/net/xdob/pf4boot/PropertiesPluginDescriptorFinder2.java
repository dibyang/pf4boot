package net.xdob.pf4boot;

import org.pf4j.PluginRuntimeException;
import org.pf4j.PropertiesPluginDescriptorFinder;
import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PropertiesPluginDescriptorFinder2
 *
 * @author yangzj
 * @version 1.0
 */
public class PropertiesPluginDescriptorFinder2 extends PropertiesPluginDescriptorFinder {
  static final Logger log = LoggerFactory.getLogger(PropertiesPluginDescriptorFinder2.class);

  public boolean isApplicable(Path pluginPath) {
    return Files.exists(pluginPath) && (Files.isDirectory(pluginPath)||FileUtils.isZipFile(pluginPath)||FileUtils.isJarFile(pluginPath));
  }

  protected Properties readProperties(Path pluginPath) {
    Properties properties = new Properties();
    if(Files.isDirectory(pluginPath)){
      Path propertiesPath = pluginPath.resolve(Paths.get(propertiesFileName));

      try (InputStream input = Files.newInputStream(propertiesPath)) {
        properties.load(input);
      } catch (IOException e) {
        throw new PluginRuntimeException(e);
      }
    }else{
      try (ZipFile zipFile = new ZipFile(pluginPath.toFile())){
        ZipEntry entry = zipFile.getEntry(propertiesFileName);
        if(entry!=null&&!entry.isDirectory()){
          properties.load(zipFile.getInputStream(entry));
        }
      } catch (IOException e) {
        throw new PluginRuntimeException(e);
      }
    }

    return properties;
  }

}
