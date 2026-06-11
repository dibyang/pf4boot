package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPluginWrapper;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PluginPathResourceResolverTest {

  @Test
  public void resolvesResourceFromStartedPluginWhenReadable() throws Exception {
    Path pluginRoot = Files.createTempDirectory("pf4boot-plugin-resource");
    Path resourcePath = pluginRoot.resolve("plugin-only.txt");
    Files.write(resourcePath, "plugin-resource".getBytes(StandardCharsets.UTF_8));
    URLClassLoader pluginClassLoader = new URLClassLoader(new java.net.URL[]{pluginRoot.toUri().toURL()});
    PluginManager pluginManager = pluginManager(Collections.singletonList(
        createPluginWrapper(pluginClassLoader, pluginRoot)));

    PluginPathResourceResolver resolver = new PluginPathResourceResolver(pluginManager);
    Resource resolved = resolver.getResource("plugin-only.txt", new ClassPathResource(""));

    assertNotNull(resolved);
    assertNotNull(resolved.getInputStream());
  }

  @Test
  public void fallsBackToSuperWhenLocationIsNotClassPathResource() throws Exception {
    Path fileResource = Files.createTempFile("pf4boot-not-classpath", ".txt");
    Files.write(fileResource, "value".getBytes(StandardCharsets.UTF_8));
    PluginManager pluginManager = pluginManager(Collections.<PluginWrapper>emptyList());
    PluginPathResourceResolver resolver = new PluginPathResourceResolver(pluginManager);

    Resource resolved = resolver.getResource("whatever", new FileSystemResource(fileResource.toFile()));
    assertNull(resolved);
  }

  @Test
  public void fallsBackToSuperWhenNoPluginResourceMatched() throws Exception {
    PluginManager pluginManager = pluginManager(Collections.<PluginWrapper>emptyList());
    PluginPathResourceResolver resolver = new PluginPathResourceResolver(pluginManager);
    String classResourcePath = PluginPathResourceResolverTest.class.getName().replace('.', '/') + ".class";

    Resource resolved = resolver.getResource(classResourcePath, new ClassPathResource(""));
    assertNotNull(resolved);
  }

  private static PluginManager pluginManager(List<PluginWrapper> plugins) {
    return (PluginManager) Proxy.newProxyInstance(
        PluginPathResourceResolverTest.class.getClassLoader(),
        new Class[]{PluginManager.class},
        (proxy, method, args) -> {
          if ("getPlugins".equals(method.getName()) && args != null && args.length == 1) {
            Object state = args[0];
            if (PluginState.STARTED.equals(state)) {
              return plugins;
            }
            return Collections.emptyList();
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

  private static PluginWrapper createPluginWrapper(ClassLoader pluginClassLoader, Path pluginPath) {
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "resource-plugin",
        "resource-plugin",
        "dummy.Class",
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    return new net.xdob.pf4boot.Pf4bootPluginWrapper(
        new DefaultPluginManager(),
        descriptor,
        pluginPath,
        pluginClassLoader);
  }
}
