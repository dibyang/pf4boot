package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.util.PluginCleanup;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 摘除宿主 MVC/JSON 派生缓存对插件类型的引用。
 * MVC 方法缓存按 loader 移除；JSON 缓存整体失效但保留 mapper 配置，随后按需预热。
 */
public final class PluginWebCacheCleaner {
  private PluginWebCacheCleaner() { }

  /** 路由排空并注销后、关闭插件上下文前调用。 */
  public static void clear(ApplicationContext context, ClassLoader loader) {
    PluginCleanup cleanup = new PluginCleanup();
    ListableBeanFactory factory = context instanceof ConfigurableApplicationContext
        ? ((ConfigurableApplicationContext) context).getBeanFactory() : context;
    Set<Object> jsonMappers = Collections.newSetFromMap(new IdentityHashMap<>());
    for (RequestMappingHandlerAdapter adapter : factory.getBeansOfType(RequestMappingHandlerAdapter.class).values()) {
      for (String field : new String[]{"sessionAttributesHandlerCache", "initBinderCache", "modelAttributeCache"}) {
        cleanup.run(field, () -> clearMap(adapter, field, key -> ownedClass(key, loader)));
      }
      cleanup.run("argument resolvers", () -> clearArguments(adapter, "argumentResolvers", loader));
      cleanup.run("binder resolvers", () -> clearArguments(adapter, "initBinderArgumentResolvers", loader));
      for (Object converter : adapter.getMessageConverters()) {
        cleanup.run("JSON converter caches", () -> clearJsonConverter(converter, jsonMappers));
      }
    }
    for (HandlerExceptionResolver resolver : factory.getBeansOfType(HandlerExceptionResolver.class).values()) {
      cleanup.run("exception resolvers", () -> clearExceptionResolver(resolver, loader));
    }
    cleanup.throwIfFailed();
  }

  /** JSON 库保持可选，只处理实际配置的 MVC converter，不创建新的 mapper 或 Bean。 */
  private static void clearJsonConverter(Object converter, Set<Object> seen) {
    Class<?> type = converter.getClass();
    while (type != null && !type.getName().equals("org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter")) {
      if (type.getName().equals("org.springframework.http.converter.json.GsonHttpMessageConverter")) {
        Object gson = call(converter, "getGson");
        if (seen.add(gson)) { clearMap(gson, "typeTokenCache", key -> true); }
        return;
      }
      type = type.getSuperclass();
    }
    if (type == null) { return; }
    Object mapper = call(converter, "getObjectMapper");
    if (!seen.add(mapper)) { return; }
    Method clear = ReflectionUtils.findMethod(mapper.getClass(), "clearCaches");
    if (clear != null) {
      ReflectionUtils.makeAccessible(clear);
      ReflectionUtils.invokeMethod(clear, mapper);
    } else {
      // Jackson 2.13 没有统一清理入口；沿用公开方法及固定版本字段适配。
      Object roots = fieldValue(mapper, "_rootDeserializers");
      if (!(roots instanceof Map)) { throw new IllegalStateException("Unsupported Jackson root deserializer cache"); }
      ((Map<?, ?>) roots).clear();
      call(call(mapper, "getSerializerProvider"), "flushCachedSerializers");
      call(fieldValue(call(mapper, "getDeserializationContext"), "_cache"), "flushCachedDeserializers");
      call(call(mapper, "getTypeFactory"), "clearCache");
    }
  }

  private static Object call(Object target, String name) {
    Method method = ReflectionUtils.findMethod(target.getClass(), name);
    if (method == null) { throw new IllegalStateException("JSON cache method missing: " + name); }
    ReflectionUtils.makeAccessible(method);
    return ReflectionUtils.invokeMethod(method, target);
  }

  private static void clearExceptionResolver(HandlerExceptionResolver resolver, ClassLoader loader) {
    if (resolver instanceof HandlerExceptionResolverComposite) {
      for (HandlerExceptionResolver child : ((HandlerExceptionResolverComposite) resolver).getExceptionResolvers()) {
        clearExceptionResolver(child, loader);
      }
    } else if (resolver instanceof ExceptionHandlerExceptionResolver) {
      clearMap(resolver, "exceptionHandlerCache", key -> ownedClass(key, loader));
      clearArguments(resolver, "argumentResolvers", loader);
    }
  }

  private static boolean ownedClass(Object key, ClassLoader loader) {
    if (!(key instanceof Class)) { return false; }
    ClassLoader owner = ((Class<?>) key).getClassLoader();
    while (owner != null) {
      if (owner == loader) { return true; }
      owner = owner.getParent();
    }
    return false;
  }

  private static void clearArguments(Object owner, String field, ClassLoader loader) {
    Object composite = fieldValue(owner, field);
    if (composite != null) {
      clearMap(composite, "argumentResolverCache", key -> key instanceof MethodParameter
          && (ownedClass(((MethodParameter) key).getDeclaringClass(), loader)
              || ownedClass(((MethodParameter) key).getContainingClass(), loader)));
    }
  }

  private static void clearMap(Object owner, String field, Predicate<Object> owned) {
    Object value = fieldValue(owner, field);
    if (!(value instanceof Map)) {
      throw new IllegalStateException("Unsupported MVC cache: " + field);
    }
    ((Map<?, ?>) value).keySet().removeIf(owned);
  }

  private static Object fieldValue(Object owner, String name) {
    Field field = ReflectionUtils.findField(owner.getClass(), name);
    if (field == null) { throw new IllegalStateException("MVC cache field missing: " + name); }
    ReflectionUtils.makeAccessible(field);
    return ReflectionUtils.getField(field, owner);
  }
}
