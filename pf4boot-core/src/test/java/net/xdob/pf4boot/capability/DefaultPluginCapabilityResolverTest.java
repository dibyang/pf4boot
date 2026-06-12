package net.xdob.pf4boot.capability;

import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultPluginCapabilityResolverTest {

  @Test
  public void readsCapabilitiesFromTrustManifest() throws Exception {
    Path pluginPath = Files.createTempFile("pf4boot-capability-provider", ".zip");
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        manifestJson().getBytes("UTF-8"));
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "provider", "provider", "Plugin", "1.0.0", "", "test", "Apache-2.0");

    PluginCapabilityDescriptor capabilities =
        new DefaultPluginCapabilityResolver().resolve(pluginPath, descriptor);

    assertEquals("provider", capabilities.getPluginId());
    assertEquals(1, capabilities.provides().size());
    assertEquals("jpa.datasource", capabilities.provides().get(0).getName());
    assertEquals("orderDs", capabilities.provides().get(0).getAttributes().get("datasource"));
    assertEquals(2, capabilities.requires().size());
    assertEquals("orderDs", capabilities.requires().get(0).getAttributes().get("datasource"));
    assertEquals("com.example.order.repository",
        capabilities.requires().get(0).getAttributes().get("repositoryPackages"));
    assertEquals("billingDs", capabilities.requires().get(1).getAttributes().get("datasource"));
    assertEquals("com.example.billing.domain",
        capabilities.requires().get(1).getAttributes().get("entityPackages"));
  }

  @Test
  public void mergesHostAndStartedPluginCapabilities() {
    PluginCapability host = new PluginCapability("web.mvc", "1", "HOST", null);
    PluginCapability datasource = new PluginCapability(
        "jpa.datasource", "1", "DATASOURCE", attributes("datasource", "orderDs"));
    PluginCapabilityDescriptor hostDescriptor =
        new DefaultPluginCapabilityDescriptor("host", Arrays.asList(host), null);
    PluginCapabilityDescriptor providerDescriptor =
        new DefaultPluginCapabilityDescriptor("provider", Arrays.asList(datasource), null);

    List<PluginCapability> capabilities = new DefaultPluginCapabilityResolver()
        .providedCapabilities(Arrays.asList(hostDescriptor, providerDescriptor));

    assertEquals(2, capabilities.size());
    assertTrue(capabilities.stream().anyMatch(capability -> "web.mvc".equals(capability.getName())));
    assertTrue(capabilities.stream().anyMatch(capability ->
        "jpa.datasource".equals(capability.getName())
            && "orderDs".equals(capability.getAttributes().get("datasource"))));
  }

  @Test
  public void missingManifestReturnsEmptyDescriptorForHistoricalPlugin() throws Exception {
    Path pluginPath = Files.createTempFile("pf4boot-historical", ".zip");
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "legacy", "legacy", "Plugin", "1.0.0", "", "test", "Apache-2.0");

    PluginCapabilityDescriptor capabilities =
        new DefaultPluginCapabilityResolver().resolve(pluginPath, descriptor);

    assertEquals("legacy", capabilities.getPluginId());
    assertTrue(capabilities.provides().isEmpty());
    assertTrue(capabilities.requires().isEmpty());
  }

  private Map<String, String> attributes(String key, String value) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(key, value);
    return attributes;
  }

  private String manifestJson() {
    return "{"
        + "\"pluginId\":\"provider\","
        + "\"pluginVersion\":\"1.0.0\","
        + "\"packageSha256\":\"abc\","
        + "\"capabilities\":{"
        + "\"provides\":[{"
        + "\"name\":\"jpa.datasource\","
        + "\"version\":\"1\","
        + "\"scope\":\"DATASOURCE\","
        + "\"attributes\":{"
        + "\"datasource\":\"orderDs\","
        + "\"transactionManager\":\"orderTransactionManager\""
        + "}"
        + "}],"
        + "\"requires\":[{"
        + "\"name\":\"jpa.datasource\","
        + "\"versionRange\":\"[1,2)\","
        + "\"required\":true,"
        + "\"attributes\":{"
        + "\"datasource\":\"orderDs\","
        + "\"entityPackages\":\"com.example.order.domain\","
        + "\"repositoryPackages\":\"com.example.order.repository\""
        + "}"
        + "},{"
        + "\"name\":\"jpa.datasource\","
        + "\"versionRange\":\"[1,2)\","
        + "\"required\":true,"
        + "\"attributes\":{"
        + "\"datasource\":\"billingDs\","
        + "\"entityPackages\":\"com.example.billing.domain\","
        + "\"repositoryPackages\":\"com.example.billing.repository\""
        + "}"
        + "}]"
        + "}"
        + "}";
  }
}
