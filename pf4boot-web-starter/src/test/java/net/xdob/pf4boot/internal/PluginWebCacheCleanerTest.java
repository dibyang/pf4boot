package net.xdob.pf4boot.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

/** 宿主 JSON 派生缓存不应在插件停止后持有插件 DTO 类型。 */
public class PluginWebCacheCleanerTest {
  private static final String PAYLOAD = PluginWebCacheCleanerTest.class.getName() + "$Payload";

  @Test
  public void clearsJsonCachesWithoutReplacingMapperOrChangingConfiguration() throws Exception {
    ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
    try (URLClassLoader loader = pluginLoader(); AnnotationConfigApplicationContext context = context(converter)) {
      exercise(mapper, loader);
      assertTrue(rootCache(mapper).size() > 0);
      PluginWebCacheCleaner.clear(context, loader);
      assertEquals(0, rootCache(mapper).size());
      assertEquals(0, ((DefaultSerializerProvider) mapper.getSerializerProvider()).cachedSerializersCount());
      assertSame(mapper, converter.getObjectMapper());
      assertSame(PropertyNamingStrategies.SNAKE_CASE, mapper.getPropertyNamingStrategy());
      assertEquals("{\"display_name\":\"host\"}", mapper.writeValueAsString(new Payload("host")));
    }
  }

  @Test
  public void clearsDistinctAndSharedConverterMappers() throws Exception {
    ObjectMapper first = new ObjectMapper(), second = new ObjectMapper();
    try (URLClassLoader loader = pluginLoader(); AnnotationConfigApplicationContext context = context(
        new MappingJackson2HttpMessageConverter(first), new MappingJackson2HttpMessageConverter(first),
        new MappingJackson2HttpMessageConverter(second))) {
      exercise(first, loader); exercise(second, loader);
      PluginWebCacheCleaner.clear(context, loader);
      assertTrue(rootCache(first).isEmpty()); assertTrue(rootCache(second).isEmpty());
    }
  }

  @Test
  public void reportsConverterFailureAndStillClearsOtherMappersBeforeRetry() throws Exception {
    ObjectMapper first = new ObjectMapper(), second = new ObjectMapper();
    FailingConverter failing = new FailingConverter(first);
    try (URLClassLoader loader = pluginLoader(); AnnotationConfigApplicationContext context = context(
        failing, new MappingJackson2HttpMessageConverter(second))) {
      exercise(first, loader); exercise(second, loader); failing.fail = true;
      try { PluginWebCacheCleaner.clear(context, loader); fail("converter failure was ignored"); }
      catch (org.pf4j.PluginRuntimeException expected) { assertNotNull(expected.getMessage()); }
      assertFalse(rootCache(first).isEmpty()); assertTrue(rootCache(second).isEmpty());
      failing.fail = false;
      PluginWebCacheCleaner.clear(context, loader);
      assertTrue(rootCache(first).isEmpty());
    }
  }

  private AnnotationConfigApplicationContext context(HttpMessageConverter<?>... converters) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
    adapter.setMessageConverters(Arrays.asList(converters));
    context.getBeanFactory().registerSingleton("adapter", adapter); context.refresh(); return context;
  }

  private void exercise(ObjectMapper mapper, ClassLoader loader) throws Exception {
    Class<?> type = loader.loadClass(PAYLOAD);
    Object value = type.getConstructor().newInstance();
    mapper.writeValueAsString(value);
    mapper.readValue("{}", type);
  }

  private Map<?, ?> rootCache(ObjectMapper mapper) {
    java.lang.reflect.Field field = ReflectionUtils.findField(ObjectMapper.class, "_rootDeserializers");
    ReflectionUtils.makeAccessible(field); return (Map<?, ?>) ReflectionUtils.getField(field, mapper);
  }

  private URLClassLoader pluginLoader() {
    URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
    return new URLClassLoader(new URL[]{url}, getClass().getClassLoader()) {
      @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!PAYLOAD.equals(name)) { return super.loadClass(name, resolve); }
        synchronized (getClassLoadingLock(name)) {
          Class<?> type = findLoadedClass(name); if (type == null) { type = findClass(name); }
          if (resolve) { resolveClass(type); } return type;
        }
      }
    };
  }

  public static class Payload {
    public String displayName = "plugin";
    public Payload() { }
    public Payload(String value) { displayName = value; }
  }

  private static class FailingConverter extends MappingJackson2HttpMessageConverter {
    boolean fail;
    FailingConverter(ObjectMapper mapper) { super(mapper); }
    @Override public ObjectMapper getObjectMapper() {
      if (fail) { throw new IllegalStateException("converter unavailable"); }
      return super.getObjectMapper();
    }
  }
}
