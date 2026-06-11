package net.xdob.pf4boot.loader;

import net.xdob.pf4boot.Pf4bootPluginManager;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginClassLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class JarPf4bootPluginLoaderTest {

  @Test
  public void loadPluginLoadsMainJarAndNestedLibResource() throws Exception {
    Path tempDir = Files.createTempDirectory("pf4boot-jar-loader");
    Path pluginJar = tempDir.resolve("plugin.jar");
    Path nestedJar = tempDir.resolve("lib/dependency.jar");
    Files.createDirectories(nestedJar.getParent());
    writeJarWithResource(nestedJar, "dependency.txt", "dependency-jar");
    writeJarWithNestedLib(pluginJar, "lib/dependency.jar", nestedJar);

    JarPf4bootPluginLoader loader = new JarPf4bootPluginLoader(createPluginManager());
    PluginClassLoader pluginClassLoader = (PluginClassLoader) loader.loadPlugin(pluginJar, new DefaultPluginDescriptor(
        "jar-loader-plugin",
        "jar-loader-plugin",
        "dummy.Class",
        "1.0.0",
        "",
        "test",
        "Apache-2.0"));

    assertNotNull(pluginClassLoader.getResource("dependency.txt"));
  }

  @Test
  public void loadPluginSkipsNonJarPaths() throws Exception {
    Path notJar = Files.createTempFile("pf4boot-no-jar", ".txt");
    assertFalse(new JarPf4bootPluginLoader(createPluginManager()).isApplicable(notJar));
  }

  private static Pf4bootPluginManager createPluginManager() {
    AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        JarPf4bootPluginLoaderTest.class.getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        (proxy, method, args) -> {
          if ("getApplicationContext".equals(method.getName())) {
            return applicationContext;
          }
          if (Object.class.equals(method.getDeclaringClass())) {
            switch (method.getName()) {
              case "toString":
                return "plugin-manager";
              case "hashCode":
                return System.identityHashCode(proxy);
              case "equals":
                return proxy == args[0];
            }
          }
          return null;
        });
  }

  private static void writeJarWithNestedLib(Path jarPath, String nestedEntryName, Path nestedJarPath) throws Exception {
    byte[] nestedJar = Files.readAllBytes(nestedJarPath);
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()), new Manifest())) {
      ZipEntry entry = new ZipEntry(nestedEntryName);
      CRC32 crc32 = new CRC32();
      crc32.update(nestedJar);
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(nestedJar.length);
      entry.setCompressedSize(nestedJar.length);
      entry.setCrc(crc32.getValue());
      out.putNextEntry(entry);
      out.write(nestedJar);
      out.closeEntry();
    }
  }

  private static void writeJarWithResource(Path jarPath, String resourceName, String value) throws Exception {
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()), new Manifest())) {
      ZipEntry entry = new ZipEntry(resourceName);
      out.putNextEntry(entry);
      out.write(value.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }
}
