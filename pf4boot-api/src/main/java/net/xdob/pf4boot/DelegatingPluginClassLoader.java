package net.xdob.pf4boot;

import net.xdob.pf4boot.util.SpringCglibCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.proxy.MethodProxy;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

public class DelegatingPluginClassLoader extends ClassLoader implements Closeable {
  static final Logger LOG = LoggerFactory.getLogger(DelegatingPluginClassLoader.class);
  private final String pluginId;
  private volatile ClassLoader delegate;

  public DelegatingPluginClassLoader(String pluginId, ClassLoader classLoader) {
    super(classLoader.getParent());
    this.pluginId = pluginId;
    this.delegate = classLoader;
  }


  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    return delegate.loadClass(name);
  }

  @Override
  public URL getResource(String name) {
    return delegate.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    return delegate.getResources(name);
  }


  @Override
  public void close() throws IOException {

    try {
      SpringCglibCleaner.clearAll(this);
      // 清除类加载器缓存的类
      Field classesField = ClassLoader.class.getDeclaredField("classes");
      classesField.setAccessible(true);
      Vector<Class<?>> classes = (Vector<Class<?>>) classesField.get(this);
      Field fieldCI = MethodProxy.class.getDeclaredField("createInfo");
      fieldCI.setAccessible(true);
      Field fieldFastCI = MethodProxy.class.getDeclaredField("fastClassInfo");
      fieldFastCI.setAccessible(true);
      for (Class<?> clazz : classes) {
        if(isCGLIBProxyClass(clazz)){
          clearCGLIBProxyClass(clazz, fieldCI, fieldFastCI);
        }
      }

      classes.clear();
    } catch (Exception e) {
      LOG.warn("clear classloader cache error", e);
    }
    this.delegate = null;
  }

  private boolean isCGLIBProxyClass(Class<?> clazz) {
    return clazz != null && clazz.getName().contains("$$"); //&& Proxy.isProxyClass(clazz)
  }

  private void clearCGLIBProxyClass(Class<?> clazz, Field fieldCI, Field fieldFastCI) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers())
          &&field.getType().isAssignableFrom(MethodProxy.class)) {
        field.setAccessible(true);
        try {
          MethodProxy methodProxy = (MethodProxy)field.get(null);
          if(methodProxy != null) {
            fieldCI.set(methodProxy, null);
            fieldFastCI.set(methodProxy, null);
          }
        } catch (Exception e) {
          LOG.warn("clear proxy createInfo error", e);
        }
      }
    }
  }


}
