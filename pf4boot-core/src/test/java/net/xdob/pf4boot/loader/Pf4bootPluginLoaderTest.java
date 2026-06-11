package net.xdob.pf4boot.loader;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import net.xdob.pf4boot.internal.Pf4bootPluginClassLoader;
import org.junit.Test;
import net.xdob.pf4boot.Pf4bootPluginManager;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Pf4bootPluginLoaderTest {

  @Test
  public void createPluginClassLoaderAddsConfiguredClasspathEntries() throws Exception {
    AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.refresh();
    Path pluginDirectory = Files.createTempDirectory("pf4boot-pl-loader-plugin");
    Path classesDirectory = pluginDirectory.resolve("classes");
    Path libDirectory = pluginDirectory.resolve("lib");
    Files.createDirectories(classesDirectory);
    Files.createDirectories(libDirectory);
    Files.write(classesDirectory.resolve("classes-resource.txt"), "class-path-resource".getBytes(StandardCharsets.UTF_8));
    createJarWithResource(libDirectory.resolve("lib-resources.jar"),
        "lib-resource.txt",
        "library-resource");

    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setClassesDirectories(Arrays.asList(classesDirectory.toAbsolutePath().toString()));
    properties.setLibDirectories(Arrays.asList(libDirectory.toAbsolutePath().toString()));
    Pf4bootPluginLoader loader = new Pf4bootPluginLoader(createPluginManager(applicationContext), properties);
    PluginDescriptor descriptor = new DefaultPluginDescriptor(
        "loader-plugin",
        "loader-plugin",
        "dummy.Class",
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    PluginClassLoader pluginClassLoader = (PluginClassLoader) loader.loadPlugin(pluginDirectory, descriptor);

    assertTrue(pluginClassLoader instanceof Pf4bootPluginClassLoader);
    assertNotNull(pluginClassLoader.getResource("classes-resource.txt"));
    assertNotNull(pluginClassLoader.getResource("lib-resource.txt"));

    pluginClassLoader.close();
    applicationContext.close();
  }

  private static Pf4bootPluginManager createPluginManager(ConfigurableApplicationContext applicationContext) {
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        Pf4bootPluginLoaderTest.class.getClassLoader(),
        new Class[]{net.xdob.pf4boot.Pf4bootPluginManager.class},
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

  private static void createJarWithResource(Path jarPath, String resourceName, String content) throws Exception {
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()), new Manifest())) {
      ZipEntry entry = new ZipEntry(resourceName);
      out.putNextEntry(entry);
      out.write(content.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }
}
