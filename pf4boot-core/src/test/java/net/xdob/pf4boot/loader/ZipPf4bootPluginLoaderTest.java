package net.xdob.pf4boot.loader;

import net.xdob.pf4boot.Pf4bootPluginManager;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginClassLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZipPf4bootPluginLoaderTest {

  @Test
  public void loadPluginExpandsZipAndLoadsNestedJarResource() throws Exception {
    Path tempDir = Files.createTempDirectory("pf4boot-zip-loader");
    Path pluginZip = tempDir.resolve("plugin.zip");
    Path nestedJar = tempDir.resolve("nested-lib.jar");
    Path cacheDirectory = Files.createTempDirectory("pf4boot-zip-loader-cache");

    writeJarWithResource(nestedJar, "dependency.txt", "dependency-jar");
    writeZipWithEntry(pluginZip, "lib/dependency.jar", nestedJar);

    PluginClassLoader pluginClassLoader = (PluginClassLoader) new ZipPf4bootPluginLoader(
        pluginManager(cacheDirectory))
        .loadPlugin(pluginZip, new DefaultPluginDescriptor(
            "zip-loader-plugin",
            "zip-loader-plugin",
            "dummy.Class",
            "1.0.0",
            "",
            "test",
            "Apache-2.0"));

    assertNotNull(pluginClassLoader.getResource("dependency.txt"));
    assertTrue(Files.exists(cacheDirectory.resolve("plugin").resolve("lib").resolve("dependency.jar")));
  }

  @Test
  public void loadPluginIgnoresNonZipFiles() throws Exception {
    Path tempFile = Files.createTempFile("pf4boot-not-plugin", ".txt");
    ZipPf4bootPluginLoader loader = new ZipPf4bootPluginLoader(
        pluginManager(Files.createTempDirectory("pf4boot-zip-loader-cache-nope")));
    assertFalse(loader.isApplicable(tempFile));
  }

  @Test
  public void expandIfZipKeepsPreviousExpansionWhenCacheIsNewer() throws Exception {
    Path tempDir = Files.createTempDirectory("pf4boot-zip-expand");
    Path zip = tempDir.resolve("plugin.zip");
    Path cacheDir = tempDir.resolve("cache");

    writeZipWithEntryAndText(zip, "marker.txt", "first");
    ZipPf4bootPluginLoader.expandIfZip(zip, cacheDir);
    String before = new String(Files.readAllBytes(cacheDir.resolve("marker.txt")), StandardCharsets.UTF_8).trim();

    writeZipWithEntryAndText(zip, "marker.txt", "second");
    Files.setLastModifiedTime(cacheDir, FileTime.fromMillis(
        Files.getLastModifiedTime(zip).toMillis() + 86400000));
    ZipPf4bootPluginLoader.expandIfZip(zip, cacheDir);
    String after = new String(Files.readAllBytes(cacheDir.resolve("marker.txt")), StandardCharsets.UTF_8).trim();

    assertEquals("first", before);
    assertEquals("first", after);
  }

  private static Pf4bootPluginManager pluginManager(Path cacheDirectory) {
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        ZipPf4bootPluginLoaderTest.class.getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        (proxy, method, args) -> {
          if ("getPluginCacheDir".equals(method.getName())) {
            return cacheDirectory;
          }
          if ("getApplicationContext".equals(method.getName())) {
            return new AnnotationConfigApplicationContext();
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

  private static void writeZipWithEntry(Path zipPath, String entryName, Path sourceFile) throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
         InputStream in = Files.newInputStream(sourceFile)) {
      ZipEntry entry = new ZipEntry(entryName);
      out.putNextEntry(entry);
      byte[] data = new byte[4096];
      int read;
      while ((read = in.read(data)) >= 0) {
        out.write(data, 0, read);
      }
      out.closeEntry();
    }
  }

  private static void writeZipWithEntryAndText(Path zipPath, String entryName, String value) throws Exception {
    Path tempFile = Files.createTempFile("pf4boot-zip-entry", ".txt");
    Files.write(tempFile, value.getBytes(StandardCharsets.UTF_8));
    writeZipWithEntry(zipPath, entryName, tempFile);
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
