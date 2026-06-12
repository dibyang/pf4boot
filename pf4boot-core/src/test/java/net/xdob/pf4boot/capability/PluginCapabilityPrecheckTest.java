package net.xdob.pf4boot.capability;

import net.xdob.pf4boot.PluginPackageVerificationMode;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginCapabilityPrecheckTest {

  private final PluginCapabilityPrecheck precheck = new PluginCapabilityPrecheck();

  @Test
  public void warnsWhenRequiredCapabilityMissing() {
    List<PluginCapabilityPrecheckResult> results = precheck.check(
        descriptor(requirement("jpa.datasource", "orderDs")),
        Collections.emptyList(),
        PluginPackageVerificationMode.WARN);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isValid());
    assertTrue(results.get(0).isWarning());
    assertEquals("PFC-002", results.get(0).getCode());
  }

  @Test
  public void rejectsWhenRequiredCapabilityMissingInEnforceMode() {
    List<PluginCapabilityPrecheckResult> results = precheck.check(
        descriptor(requirement("jpa.datasource", "orderDs")),
        Collections.emptyList(),
        PluginPackageVerificationMode.ENFORCE);

    assertEquals(1, results.size());
    assertFalse(results.get(0).isValid());
    assertEquals("PFC-002", results.get(0).getCode());
  }

  @Test
  public void matchesDatasourceByName() {
    List<PluginCapabilityPrecheckResult> results = precheck.check(
        descriptor(requirement("jpa.datasource", "orderDs")),
        Collections.singletonList(capability("jpa.datasource", "orderDs")),
        PluginPackageVerificationMode.ENFORCE);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isValid());
    assertFalse(results.get(0).isWarning());
    assertEquals("OK", results.get(0).getCode());
  }

  @Test
  public void ignoresJpaConsumerPackageScanAttributesWhenMatchingProvider() {
    Map<String, String> attributes = datasourceAttributes("orderDs");
    attributes.put("entityPackages", "com.example.order.domain");
    attributes.put("repositoryPackages", "com.example.order.repository");
    List<PluginCapabilityPrecheckResult> results = precheck.check(
        descriptor(new PluginCapabilityRequirement("jpa.datasource", "[1,2)", true, attributes)),
        Collections.singletonList(capability("jpa.datasource", "orderDs")),
        PluginPackageVerificationMode.ENFORCE);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isValid());
    assertEquals("OK", results.get(0).getCode());
  }

  private PluginCapabilityDescriptor descriptor(PluginCapabilityRequirement requirement) {
    return new DefaultPluginCapabilityDescriptor(
        "consumer",
        null,
        Collections.singletonList(requirement));
  }

  private PluginCapabilityRequirement requirement(String name, String datasource) {
    return new PluginCapabilityRequirement(name, "[1,2)", true, datasourceAttributes(datasource));
  }

  private PluginCapability capability(String name, String datasource) {
    return new PluginCapability(name, "1", "DATASOURCE", datasourceAttributes(datasource));
  }

  private Map<String, String> datasourceAttributes(String datasource) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("datasource", datasource);
    return attributes;
  }
}
