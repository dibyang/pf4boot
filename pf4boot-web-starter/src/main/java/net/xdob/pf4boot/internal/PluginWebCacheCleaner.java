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
import java.util.Map;
import java.util.function.Predicate;

/**
 * 摘除宿主 MVC 方法缓存对插件类型的引用。
 * Spring 5.3 的这些缓存没有公开清理 API；仅移除当前插件拥有的键，保留其它插件与宿主缓存。
 */
public final class PluginWebCacheCleaner {
  private PluginWebCacheCleaner() { }

  /** 路由排空并注销后、关闭插件上下文前调用。 */
  public static void clear(ApplicationContext context, ClassLoader loader) {
    PluginCleanup cleanup = new PluginCleanup();
    ListableBeanFactory factory = context instanceof ConfigurableApplicationContext
        ? ((ConfigurableApplicationContext) context).getBeanFactory() : context;
    for (RequestMappingHandlerAdapter adapter : factory.getBeansOfType(RequestMappingHandlerAdapter.class).values()) {
      for (String field : new String[]{"sessionAttributesHandlerCache", "initBinderCache", "modelAttributeCache"}) {
        cleanup.run(field, () -> clearMap(adapter, field, key -> ownedClass(key, loader)));
      }
      cleanup.run("argument resolvers", () -> clearArguments(adapter, "argumentResolvers", loader));
      cleanup.run("binder resolvers", () -> clearArguments(adapter, "initBinderArgumentResolvers", loader));
    }
    for (HandlerExceptionResolver resolver : factory.getBeansOfType(HandlerExceptionResolver.class).values()) {
      cleanup.run("exception resolvers", () -> clearExceptionResolver(resolver, loader));
    }
    cleanup.throwIfFailed();
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
