package org.springframework.beans.factory.support;

import net.xdob.pf4boot.util.PluginCleanup;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * 观察 Spring 5.3 已选定的销毁回调，避免异常被 Spring 日志处理吞掉。
 * 只用于插件 BeanFactory，不替换宿主的 Spring 类；元数据不匹配时阻断清理完成。
 */
public final class PluginDisposableBeanAdapter {
  private PluginDisposableBeanAdapter() { }

  /** 包装已注册的销毁对象；失败回调交给现有清理器重试。 */
  public static void destroy(DisposableBean disposable, PluginCleanup failures) {
    if (!(disposable instanceof DisposableBeanAdapter)) {
      failures.run("custom destruction", () -> {
        try { disposable.destroy(); } catch (Exception e) { throw new IllegalStateException(e); }
      });
      return;
    }
    failures.run("Spring destruction metadata", () -> destroySelected(disposable, failures));
  }

  /** 为直接注册、没有 BeanDefinition 的实例执行标准销毁选择。 */
  public static void destroyInstance(Object bean, List<DestructionAwareBeanPostProcessor> processors,
      AccessControlContext context, PluginCleanup failures) {
    destroy(new DisposableBeanAdapter(bean, processors, context), failures);
  }

  @SuppressWarnings("unchecked")
  private static void destroySelected(Object adapter, PluginCleanup failures) {
    Object bean = field(adapter, "bean");
    String name = (String) field(adapter, "beanName");
    List<DestructionAwareBeanPostProcessor> processors =
        (List<DestructionAwareBeanPostProcessor>) field(adapter, "beanPostProcessors");
    if (processors != null) {
      for (DestructionAwareBeanPostProcessor processor : processors) {
        Method callback = ReflectionUtils.findMethod(processor.getClass(), "postProcessBeforeDestruction", Object.class, String.class);
        if (processor instanceof InitDestroyAnnotationBeanPostProcessor && callback != null
            && callback.getDeclaringClass() == InitDestroyAnnotationBeanPostProcessor.class) {
          annotationCallbacks(processor, bean, name, failures);
        } else {
          failures.run(name + " destruction processor", () -> processor.postProcessBeforeDestruction(bean, name));
        }
      }
    }
    AccessControlContext context = (AccessControlContext) field(adapter, "acc");
    if (Boolean.TRUE.equals(field(adapter, "invokeDisposableBean"))) {
      callback(name + ".destroy", context, failures, () -> {
        try { ((DisposableBean) bean).destroy(); } catch (Exception e) { throw new IllegalStateException(e); }
      });
    }
    if (Boolean.TRUE.equals(field(adapter, "invokeAutoCloseable"))) {
      callback(name + ".close", context, failures, () -> {
        try { ((AutoCloseable) bean).close(); } catch (Exception e) { throw new IllegalStateException(e); }
      });
    } else {
      Method method = (Method) field(adapter, "destroyMethod");
      if (method != null) {
        Object[] args = method.getParameterCount() == 0 ? new Object[0] : new Object[]{Boolean.TRUE};
        callback(name + "." + method.getName(), context, failures, () -> invoke(method, bean, args));
      }
    }
  }

  /** 使用 Spring 已检查过的注解元数据，保留继承顺序及重复方法排除规则。 */
  private static void annotationCallbacks(Object processor, Object bean, String name, PluginCleanup failures) {
    Method find = ReflectionUtils.findMethod(InitDestroyAnnotationBeanPostProcessor.class,
        "findLifecycleMetadata", Class.class);
    Object metadata = invoke(find, processor, bean.getClass());
    Object checked = field(metadata, "checkedDestroyMethods");
    Collection<?> methods = (Collection<?>) (checked != null ? checked : field(metadata, "destroyMethods"));
    for (Object element : methods) {
      Method method = (Method) field(element, "method");
      failures.run(name + " annotation " + method.getName(), () -> invoke(method, bean));
    }
  }

  private static void callback(String step, AccessControlContext context, PluginCleanup failures, Runnable action) {
    failures.run(step, () -> {
      if (System.getSecurityManager() == null) { action.run(); }
      else {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> { action.run(); return null; }, context);
      }
    });
  }

  private static Object field(Object target, String name) {
    Field field = ReflectionUtils.findField(target.getClass(), name);
    if (field == null) { throw new IllegalStateException("Unsupported Spring destruction field: " + name); }
    ReflectionUtils.makeAccessible(field);
    return ReflectionUtils.getField(field, target);
  }

  private static Object invoke(Method method, Object target, Object... args) {
    if (method == null) { throw new IllegalStateException("Unsupported Spring destruction method"); }
    ReflectionUtils.makeAccessible(method);
    return ReflectionUtils.invokeMethod(method, target, args);
  }
}
