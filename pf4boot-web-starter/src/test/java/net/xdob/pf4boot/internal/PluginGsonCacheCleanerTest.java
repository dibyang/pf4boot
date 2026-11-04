package net.xdob.pf4boot.internal;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/** 无 Jackson 的 Gson converter 也应释放插件 DTO 派生缓存。 */
public class PluginGsonCacheCleanerTest {
  @Test
  public void clearsGsonTypeAdaptersWithoutReplacingOrReconfiguringGson() throws Exception {
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    GsonHttpMessageConverter converter = new GsonHttpMessageConverter(); converter.setGson(gson);
    String name = getClass().getName() + "$Payload";
    URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader loader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader()) {
      @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(requested)) { return super.loadClass(requested, resolve); }
        synchronized (getClassLoadingLock(requested)) {
          Class<?> type = findLoadedClass(requested); if (type == null) { type = findClass(requested); }
          if (resolve) { resolveClass(type); } return type;
        }
      }
    }; AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
      adapter.setMessageConverters(Collections.singletonList(converter));
      context.getBeanFactory().registerSingleton("adapter", adapter); context.refresh();
      Class<?> payload = loader.loadClass(name);
      MockHttpOutputMessage output = new MockHttpOutputMessage();
      converter.write(payload.getConstructor().newInstance(), MediaType.APPLICATION_JSON, output);
      assertTrue(output.getBodyAsString().contains("display_name"));
      MockHttpInputMessage input = new MockHttpInputMessage(output.getBodyAsBytes());
      assertEquals("plugin", payload.getField("displayName").get(converter.read(payload, input)));
      Field field = ReflectionUtils.findField(Gson.class, "typeTokenCache"); ReflectionUtils.makeAccessible(field);
      Map<?, ?> cache = (Map<?, ?>) ReflectionUtils.getField(field, gson);
      assertFalse(cache.isEmpty());
      PluginWebCacheCleaner.clear(context, loader);
      assertTrue(cache.isEmpty()); assertSame(gson, converter.getGson());
      assertEquals("{\"display_name\":\"plugin\"}", gson.toJson(new Payload()));
    }
  }
  public static class Payload { public String displayName = "plugin"; }
}
