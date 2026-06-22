package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.PluginPackageVerificationMode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Pf4bootPropertiesTest {

  @Test
  public void productionProfileEnforcesPackageTrustAndRepositoryGates() {
    Pf4bootProperties properties = new Pf4bootProperties();

    assertFalse(properties.isProductionProfileEnabled());
    assertEquals(PluginPackageVerificationMode.DISABLED, properties.getPluginPackageVerificationMode());
    assertEquals(PluginPackageVerificationMode.WARN, properties.getPluginRepositoryTrustMode());
    assertFalse(properties.isPluginRepositoryReleaseGateEnabled());
    assertFalse(properties.isPluginPackageSignatureRequired());

    properties.setProductionProfileEnabled(true);

    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginPackageVerificationMode());
    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginPackageTrustMode());
    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginCompatibilityVerificationMode());
    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginCapabilityPrecheckMode());
    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginCompatibilityPrecheckMode());
    assertEquals(PluginPackageVerificationMode.ENFORCE, properties.getPluginRepositoryTrustMode());
    assertTrue(properties.isPluginRepositoryReleaseGateEnabled());
    assertTrue(properties.isPluginPackageSignatureRequired());
  }
}
