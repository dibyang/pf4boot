package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.PluginPackageVerificationMode;
import net.xdob.pf4boot.deployment.PluginDeploymentMetricsSnapshot;
import net.xdob.pf4boot.diagnostic.PluginCleanupReport;
import net.xdob.pf4boot.diagnostic.PluginConcurrencyReport;
import net.xdob.pf4boot.diagnostic.PluginLifecycleDiagnostic;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.PluginState;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Pf4bootGovernanceEndpointTest {

  @Test
  public void summaryIncludesGovernanceConfigurationAndDeploymentMetrics() {
    PluginRuntimeSnapshot started = snapshot("started", PluginState.STARTED, null);
    PluginRuntimeSnapshot failed = snapshot("failed", PluginState.FAILED, "boom");
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.WARN);
    properties.setPluginCapabilityPrecheckMode(PluginPackageVerificationMode.ENFORCE);
    properties.setPluginCompatibilityPrecheckMode(PluginPackageVerificationMode.WARN);
    properties.setPluginRepositoryEnabled(true);
    properties.setPluginRepositoryLocation("repo");
    PluginDeploymentMetricsSnapshot deploymentMetrics =
        new PluginDeploymentMetricsSnapshot(3, 1, 2, 99);
    Pf4bootGovernanceEndpoint endpoint = new Pf4bootGovernanceEndpoint(
        () -> Arrays.asList(started, failed),
        () -> deploymentMetrics,
        new PluginLifecycleDiagnostic() {
          @Override
          public PluginCleanupReport inspectAfterStop(String pluginId, ClassLoader pluginClassLoader) {
            return new PluginCleanupReport(
                pluginId,
                true,
                0,
                0,
                0,
                0,
                false,
                Collections.singletonList("clean"));
          }

          @Override
          public PluginConcurrencyReport inspectLifecycleLocks() {
            return null;
          }
        },
        properties);

    Pf4bootGovernanceSnapshot summary = endpoint.summary();

    assertEquals(2, summary.getPluginCount());
    assertEquals(1, summary.getStartedPluginCount());
    assertEquals(1, summary.getFailedPluginCount());
    assertEquals("WARN", summary.getTrustMode());
    assertEquals("ENFORCE", summary.getCapabilityPrecheckMode());
    assertEquals("WARN", summary.getCompatibilityPrecheckMode());
    assertEquals(".pf4boot-trust.json", summary.getTrustManifestExtension());
    assertTrue(summary.isRepositoryEnabled());
    assertEquals("offline-index", summary.getRepositoryType());
    assertTrue(summary.isRepositoryLocationConfigured());
    assertEquals(3, summary.getDeploymentSummary().getDeploymentTotal());
    assertEquals(2, summary.getCleanupReports().size());
    assertTrue(summary.getWarnings().isEmpty());
  }

  @Test
  public void summaryReturnsWarningsWhenOptionalProvidersAreUnavailable() {
    PluginRuntimeSnapshot snapshot = snapshot("sample", PluginState.STARTED, null);
    Pf4bootGovernanceEndpoint endpoint = new Pf4bootGovernanceEndpoint(
        () -> Collections.singletonList(snapshot),
        null,
        null,
        null);

    Pf4bootGovernanceSnapshot summary = endpoint.summary();

    assertEquals(1, summary.getPluginCount());
    assertEquals("UNKNOWN", summary.getTrustMode());
    assertNull(summary.getDeploymentSummary());
    assertTrue(summary.getCleanupReports().isEmpty());
    assertTrue(summary.getWarnings().contains("cleanup diagnostic unavailable"));
  }

  @Test
  public void summaryKeepsEndpointAvailableWhenCleanupDiagnosticFails() {
    PluginRuntimeSnapshot snapshot = snapshot("sample", PluginState.STARTED, null);
    Pf4bootGovernanceEndpoint endpoint = new Pf4bootGovernanceEndpoint(
        () -> Collections.singletonList(snapshot),
        null,
        new PluginLifecycleDiagnostic() {
          @Override
          public PluginCleanupReport inspectAfterStop(String pluginId, ClassLoader pluginClassLoader) {
            throw new IllegalStateException("diagnostic unavailable");
          }

          @Override
          public PluginConcurrencyReport inspectLifecycleLocks() {
            return null;
          }
        },
        new Pf4bootProperties());

    Pf4bootGovernanceSnapshot summary = endpoint.summary();

    assertEquals(1, summary.getPluginCount());
    assertTrue(summary.getCleanupReports().isEmpty());
    assertTrue(summary.getWarnings().contains("cleanup diagnostic failed: sample"));
  }

  private PluginRuntimeSnapshot snapshot(String pluginId, PluginState state, String errorMessage) {
    PluginRuntimeSnapshot snapshot = new PluginRuntimeSnapshot();
    snapshot.setPluginId(pluginId);
    snapshot.setState(state);
    snapshot.setErrorMessage(errorMessage);
    return snapshot;
  }
}
