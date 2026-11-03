package net.xdob.pf4boot;

import org.junit.Test;
import org.pf4j.PluginRuntimeException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.*;
import javax.annotation.PreDestroy;
import java.util.*;
import static org.junit.Assert.*;

public class PluginDestructionTest {
  private static boolean failing;
  private static final Map<String, Integer> calls = new HashMap<>();
  private static void invoke(String name, boolean canFail) {
    calls.put(name, calls.getOrDefault(name, 0) + 1);
    if (canFail && failing) { throw new IllegalStateException(name); }
  }
  public static class AnnotatedParent {
    @PreDestroy private void parent() { invoke("parent", false); }
  }
  public static class Annotated extends AnnotatedParent implements DisposableBean {
    @PreDestroy private void annotated() { invoke("annotation", true); }
    public void destroy() { invoke("disposable", true); }
    public void shutdown(boolean force) { assertTrue(force); invoke("custom", true); }
  }
  public static class CloseableResource implements AutoCloseable {
    public void close() { invoke("close", true); }
  }
  public static class Once implements DisposableBean {
    @PreDestroy public void destroy() { invoke("deduplicated", false); }
  }
  @Configuration public static class Config {
    @Bean public static org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor customProcessor() {
      return new org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor() {
        public boolean requiresDestruction(Object bean) { return bean instanceof Once; }
        public void postProcessBeforeDestruction(Object bean, String name) { invoke("custom-processor", false); }
      };
    }
    @Bean(destroyMethod = "shutdown") public Annotated annotated() { return new Annotated(); }
    @Bean public CloseableResource closeableResource() { return new CloseableResource(); }
    @Bean public Once once() { return new Once(); }
  }
  @Test public void capturesEveryStandardDestroyFailureAndRetriesOnlyFailedCallbacks() {
    calls.clear(); failing = true;
    PluginListableBeanFactory factory = new PluginListableBeanFactory(getClass().getClassLoader());
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(factory);
    context.register(Config.class); context.refresh(); context.close();
    try { factory.verifyDestruction(); fail("must report destruction failures"); }
    catch (PluginRuntimeException expected) { assertEquals(4, expected.getSuppressed().length); }
    assertEquals(Integer.valueOf(1), calls.get("parent"));
    assertEquals(Integer.valueOf(1), calls.get("deduplicated"));
    assertEquals(Integer.valueOf(1), calls.get("custom-processor"));
    failing = false; factory.retryDestruction(); factory.verifyDestruction();
    for (String failed : Arrays.asList("annotation", "disposable", "custom", "close")) {
      assertEquals(failed, Integer.valueOf(2), calls.get(failed));
    }
    assertEquals(Integer.valueOf(1), calls.get("parent"));
    assertEquals(Integer.valueOf(1), calls.get("deduplicated"));
    assertEquals(Integer.valueOf(1), calls.get("custom-processor"));
  }

  @Test public void directInstanceDoesNotGainAnInferredCloseCallbackDuringUpgrade() {
    PluginListableBeanFactory factory = new PluginListableBeanFactory(getClass().getClassLoader());
    java.util.concurrent.atomic.AtomicInteger closes = new java.util.concurrent.atomic.AtomicInteger();
    AutoCloseable direct = closes::incrementAndGet;
    factory.destroyBean(direct);
    factory.verifyDestruction();
    assertEquals("preserve Spring direct-instance destruction selection", 0, closes.get());
  }
}
