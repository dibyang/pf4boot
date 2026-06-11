package org.springframework.web.servlet.mvc.method;

import net.xdob.pf4boot.DefaultAutoExportMgr;
import net.xdob.pf4boot.DefaultShareBeanMgr;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManagerImpl;
import net.xdob.pf4boot.Pf4bootPluginSupport;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PluginRequestMappingHandlerMappingTest {

  private AnnotationConfigApplicationContext applicationContext;
  private TestPluginManager pluginManager;
  private PluginRequestMappingHandlerMapping mapping;
  private TestPlugin plugin;
  private StaticWebApplicationContext webContext;

  @Before
  public void setUp() throws Exception {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(
        "metadataReaderFactory",
        MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    applicationContext.refresh();

    Path pluginsRoot = Files.createTempDirectory("pf4boot-web-test");
    pluginManager = new TestPluginManager(applicationContext, pluginsRoot);
    plugin = pluginManager.createPlugin("web-plugin");

    webContext = new StaticWebApplicationContext();
    webContext.setServletContext(new MockServletContext());
    webContext.refresh();

    mapping = new PluginRequestMappingHandlerMapping();
    mapping.setApplicationContext(webContext);
    mapping.afterPropertiesSet();
  }

  @After
  public void tearDown() {
    if (plugin != null && plugin.getPluginContext() != null) {
      plugin.getPluginContext().close();
    }
    if (pluginManager != null) {
      pluginManager.close();
    }
    if (webContext != null) {
      webContext.close();
    }
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  public void registersAndUnregistersPluginControllers() throws Exception {
    mapping.registerControllers(plugin);

    assertEquals(1, mapping.getHandlerMethods().size());
    assertNotNull(handlerFor("/plugin/ping"));

    mapping.unregisterControllers(plugin);

    assertEquals(0, mapping.getHandlerMethods().size());
    assertNull(handlerFor("/plugin/ping"));
  }

  @Test
  public void repeatedControllerRegistrationDoesNotDuplicateMappings() {
    mapping.registerControllers(plugin);
    mapping.registerControllers(plugin);

    assertEquals(1, mapping.getHandlerMethods().size());

    mapping.unregisterControllers(plugin);
    assertEquals(0, mapping.getHandlerMethods().size());
  }

  @Test
  public void registersAndUnregistersPluginInterceptors() throws Exception {
    mapping.registerControllers(plugin);
    mapping.registerInterceptors(plugin);

    HandlerExecutionChain chain = handlerFor("/plugin/ping");
    assertNotNull(chain);
    assertEquals(2, chain.getInterceptors().length);

    mapping.unregisterInterceptors(plugin);

    chain = handlerFor("/plugin/ping");
    assertNotNull(chain);
    assertEquals(1, chain.getInterceptors().length);
    assertEquals(0, mapping.getDynamicInterceptorCount());
  }

  @Test
  public void drainingPluginRejectsNewRequests() throws Exception {
    mapping.registerControllers(plugin);
    mapping.beginDrain(java.util.Collections.singletonList("web-plugin"));

    MockHttpServletRequest request = request("/plugin/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();
    HandlerExecutionChain chain = mapping.getHandler(request);

    assertNotNull(chain);
    assertFalse(chain.getInterceptors()[0].preHandle(request, response, chain.getHandler()));
    assertEquals(503, response.getStatus());
    assertEquals(0, mapping.getInFlightRequestCount("web-plugin"));
  }

  @Test
  public void awaitDrainWaitsForInFlightRequests() throws Exception {
    mapping.registerControllers(plugin);

    MockHttpServletRequest request = request("/plugin/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();
    HandlerExecutionChain chain = mapping.getHandler(request);

    assertNotNull(chain);
    assertTrue(chain.getInterceptors()[0].preHandle(request, response, chain.getHandler()));
    assertEquals(1, mapping.getInFlightRequestCount("web-plugin"));

    mapping.beginDrain(java.util.Collections.singletonList("web-plugin"));
    assertFalse(mapping.awaitDrain(java.util.Collections.singletonList("web-plugin"), 30));

    chain.getInterceptors()[0].afterCompletion(request, response, chain.getHandler(), null);

    assertTrue(mapping.awaitDrain(java.util.Collections.singletonList("web-plugin"), 30));
    assertEquals(0, mapping.getInFlightRequestCount("web-plugin"));
  }

  @Test
  public void cleanupVerifierReportsNoWebResidueAfterUnregister() {
    mapping.registerControllers(plugin);
    mapping.registerInterceptors(plugin);

    assertEquals(1, mapping.getRegisteredHandlerCount("web-plugin"));
    assertEquals(1, mapping.getRegisteredInterceptorCount("web-plugin"));

    mapping.unregisterControllers(plugin);
    mapping.unregisterInterceptors(plugin);

    assertTrue(mapping.verifyStoppedPlugin("web-plugin", plugin.getWrapper().getPluginClassLoader())
        .stream()
        .noneMatch(result -> result.isError()));
  }

  @Test(expected = IllegalStateException.class)
  public void dynamicMvcBeanNameConflictIsRejected() {
    webContext.getBeanFactory().registerSingleton("testController", new Object());

    mapping.registerControllers(plugin);
  }

  private HandlerExecutionChain handlerFor(String path) throws Exception {
    return mapping.getHandler(request(path));
  }

  private MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }

  private static class TestPluginManager extends Pf4bootPluginManagerImpl {

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot) {
      super(applicationContext, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), pluginsRoot);
    }

    TestPlugin createPlugin(String pluginId) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
          pluginId, pluginId, TestPlugin.class.getName(), "1.0.0", "", "test", "Apache-2.0");
      ClassLoader pluginClassLoader = new TestPluginClassLoader(TestPlugin.class.getClassLoader());
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
          this, descriptor, getPluginsRoot().resolve(pluginId), pluginClassLoader);
      wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(pluginId, wrapper);
      pluginClassLoaders.put(pluginId, pluginClassLoader);

      AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext();
      pluginContext.register(WebPluginConfig.class);
      pluginContext.refresh();
      return new TestPlugin(wrapper, pluginContext);
    }
  }

  private static class TestPluginClassLoader extends ClassLoader {
    TestPluginClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  private static class TestPlugin extends Pf4bootPlugin {
    private final AnnotationConfigApplicationContext pluginContext;

    TestPlugin(PluginWrapper wrapper, AnnotationConfigApplicationContext pluginContext) {
      super(wrapper);
      this.pluginContext = pluginContext;
    }

    @Override
    public AnnotationConfigApplicationContext getPluginContext() {
      return pluginContext;
    }
  }

  @Configuration
  public static class WebPluginConfig {
    @Bean
    public TestController testController() {
      return new TestController();
    }

    @Bean
    public HandlerInterceptor testInterceptor() {
      return new HandlerInterceptor() {
        @Override
        public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
          return true;
        }
      };
    }
  }

  @Controller
  public static class TestController {
    @GetMapping("/plugin/ping")
    public String ping() {
      return "ok";
    }
  }
}
