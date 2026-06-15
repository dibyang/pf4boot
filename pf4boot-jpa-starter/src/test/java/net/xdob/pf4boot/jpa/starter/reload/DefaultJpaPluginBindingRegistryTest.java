package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.starter.JpaPluginBinding;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DefaultJpaPluginBindingRegistryTest {

  @Test
  public void registryReturnsImmutableSnapshotAndCleansRemovedBinding() {
    DefaultJpaPluginBindingRegistry registry = new DefaultJpaPluginBindingRegistry();
    registry.register(binding("consumer", "demo"));

    assertEquals(1, registry.findByDomainId("demo").size());
    Map<String, JpaPluginBinding> snapshot = registry.snapshot();
    try {
      snapshot.clear();
      fail("snapshot should be immutable");
    } catch (UnsupportedOperationException expected) {
      // expected
    }

    registry.remove("consumer");

    assertEquals(0, registry.findByDomainId("demo").size());
    assertEquals(1, snapshot.size());
  }

  private static JpaPluginBinding binding(String pluginId, String domainId) {
    return new JpaPluginBinding(
        pluginId,
        Pf4bootJpaProperties.Mode.SHARED,
        domainId,
        null,
        null,
        null,
        Collections.emptyList(),
        true);
  }
}
