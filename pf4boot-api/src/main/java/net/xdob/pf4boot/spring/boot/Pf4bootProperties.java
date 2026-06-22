package net.xdob.pf4boot.spring.boot;

import org.pf4j.PluginLoader;
import org.pf4j.RuntimeMode;
import net.xdob.pf4boot.PluginPackageVerificationMode;
import net.xdob.pf4boot.modal.DynamicBeanConflictPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = Pf4bootProperties.PREFIX)
public class Pf4bootProperties {

  public static final String PREFIX = "spring.pf4boot";
  /**
   * properties define under this property will be passed to
   * plugin `ApplicationContext` environment.
   */
  Map<String, Object> pluginProperties = new HashMap<>();
  private boolean enabled = false;

  private boolean pluginAdminEnabled = true;
  /**
   * Enables fail-closed production governance defaults for plugin package, trust,
   * compatibility, capability, and repository release gates.
   */
  private boolean productionProfileEnabled = false;
  /**
   * Plugin package verification mode before class loader creation.
   */
  private PluginPackageVerificationMode pluginPackageVerificationMode = PluginPackageVerificationMode.DISABLED;
  /**
   * Plugin compatibility verification mode before class loader creation.
   */
  private PluginPackageVerificationMode pluginCompatibilityVerificationMode = PluginPackageVerificationMode.DISABLED;
  /**
   * Sidecar checksum file extension used by the default verifier.
   */
  private String pluginPackageChecksumExtension = ".sha256";
  /**
   * Plugin trust manifest verification mode before class loader creation.
   */
  private PluginPackageVerificationMode pluginPackageTrustMode = PluginPackageVerificationMode.DISABLED;
  /**
   * Requires signature metadata in trust manifests. Production profile enables
   * this effective gate even when the raw property remains false.
   */
  private boolean pluginPackageSignatureRequired = false;
  /**
   * Requires cryptographic signature verification when signature metadata is
   * present. Production profile enables this effective gate.
   */
  private boolean pluginPackageSignatureVerificationRequired = false;
  /**
   * Plugin capability precheck mode before deployment replacement.
   */
  private PluginPackageVerificationMode pluginCapabilityPrecheckMode = PluginPackageVerificationMode.DISABLED;
  /**
   * Plugin framework and Spring Boot compatibility precheck mode before deployment replacement.
   */
  private PluginPackageVerificationMode pluginCompatibilityPrecheckMode = PluginPackageVerificationMode.DISABLED;
  /**
   * Framework version used for trust manifest pf4bootVersionRange checks.
   */
  private String pluginCompatibilityPf4bootVersion = "0.0.0";
  /**
   * Spring Boot version used for trust manifest springBootVersionRange checks.
   */
  private String pluginCompatibilitySpringBootVersion = "";
  /**
   * PF4J version used for trust manifest pf4jVersionRange checks.
   */
  private String pluginCompatibilityPf4jVersion = "3.15.0";
  /**
   * pf4boot-plugin Gradle plugin version used for trust manifest pf4bootPluginVersionRange checks.
   */
  private String pluginCompatibilityPf4bootPluginVersion = "1.7.0";
  /**
   * JDK version used for trust manifest jdkVersionRange checks.
   */
  private String pluginCompatibilityJdkVersion = "1.8";
  /**
   * Plugin package format version used for trust manifest packageFormatVersionRange checks.
   */
  private String pluginCompatibilityPackageFormatVersion = "1";
  /**
   * Enables offline plugin repository resolution.
   */
  private boolean pluginRepositoryEnabled = false;
  /**
   * Plugin repository type. The first implementation supports offline-index.
   */
  private String pluginRepositoryType = "offline-index";
  /**
   * Local or mounted offline repository location.
   */
  private String pluginRepositoryLocation = "";
  /**
   * Repository index trust mode.
   */
  private PluginPackageVerificationMode pluginRepositoryTrustMode = PluginPackageVerificationMode.WARN;
  /**
   * Requires repository release metadata to carry an approval gate before real
   * repository replacement. Production profile enables this effective gate.
   */
  private boolean pluginRepositoryReleaseGateEnabled = false;
  /**
   * Release record attribute name used as the repository release gate.
   */
  private String pluginRepositoryReleaseGateAttribute = "releaseGate";
  /**
   * Required release gate attribute value.
   */
  private String pluginRepositoryReleaseGateValue = "passed";
  /**
   * Local staging/cache directory for repository packages.
   */
  private String pluginRepositoryCacheDirectory = "";
  /**
   * Allows repository releases to execute real replacement. Disabled by default.
   */
  private boolean pluginRepositoryReplaceEnabled = false;
  /**
   * Plugin trust manifest file extension.
   */
  private String pluginPackageTrustManifestExtension = ".pf4boot-trust.json";
  /**
   * Plugin trust root identifiers (预留：后续可用于签名根密钥映射).
   */
  private String[] pluginPackageTrustRoots = new String[0];
  /**
   * Public keys bound by trust root id. Values may be PEM public keys or raw
   * base64 X.509 SubjectPublicKeyInfo content.
   */
  private Map<String, String> pluginPackageTrustRootPublicKeys = new HashMap<>();
  /**
   * Strategy for dynamically registered bean name conflicts.
   */
  private DynamicBeanConflictPolicy dynamicBeanConflictPolicy = DynamicBeanConflictPolicy.REJECT;
  /**
   * Maximum time to wait for plugin draining before replacement stops plugins.
   */
  private long pluginDrainTimeoutMillis = 30000;
  /**
   * Auto start plugin when main app is ready
   */
  private boolean autoStartPlugin = true;
  /**
   * Plugins disabled by default
   */
  private String[] disabledPlugins;
  /**
   * Plugins enabled by default, prior to `disabledPlugins`
   */
  private String[] enabledPlugins;
  /**
   * Set to true to allow requires expression to be exactly x.y.z. The default is
   * false, meaning that using an exact version x.y.z will implicitly mean the
   * same as >=x.y.z
   */
  private boolean exactVersionAllowed = false;
  /**
   * Extended Plugin Class Directory
   */
  private List<String> classesDirectories = new ArrayList<>();
  /**
   * Extended Plugin Jar Directory
   */
  private List<String> libDirectories = new ArrayList<>();
  /**
   * Runtime Mode：development/deployment
   */
  private RuntimeMode runtimeMode = RuntimeMode.DEPLOYMENT;
  /**
   * Plugin root directory: default “plugins”; when non-jar mode plugin, the value
   * should be an absolute directory address
   */
  private String pluginsRoot = "plugins";
  /**
   * Allows to provide custom plugin loaders
   */
  private Class<PluginLoader> customPluginLoader;
  /**
   * Profile for plugin Spring {@link ApplicationContext}
   */
  private String[] pluginProfiles = new String[]{"plugin"};
  /**
   * The system version used for comparisons to the plugin requires attribute.
   */
  private String systemVersion = "0.0.0";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isPluginAdminEnabled() {
    return pluginAdminEnabled;
  }

  public void setPluginAdminEnabled(boolean pluginAdminEnabled) {
    this.pluginAdminEnabled = pluginAdminEnabled;
  }

  public boolean isProductionProfileEnabled() {
    return productionProfileEnabled;
  }

  public void setProductionProfileEnabled(boolean productionProfileEnabled) {
    this.productionProfileEnabled = productionProfileEnabled;
  }

  public PluginPackageVerificationMode getPluginPackageVerificationMode() {
    return productionMode(pluginPackageVerificationMode);
  }

  public void setPluginPackageVerificationMode(PluginPackageVerificationMode pluginPackageVerificationMode) {
    this.pluginPackageVerificationMode = pluginPackageVerificationMode == null
        ? PluginPackageVerificationMode.DISABLED
        : pluginPackageVerificationMode;
  }

  public PluginPackageVerificationMode getPluginCompatibilityVerificationMode() {
    return productionMode(pluginCompatibilityVerificationMode);
  }

  public void setPluginCompatibilityVerificationMode(PluginPackageVerificationMode pluginCompatibilityVerificationMode) {
    this.pluginCompatibilityVerificationMode = pluginCompatibilityVerificationMode == null
        ? PluginPackageVerificationMode.DISABLED
        : pluginCompatibilityVerificationMode;
  }

  public String getPluginPackageChecksumExtension() {
    return pluginPackageChecksumExtension;
  }

  public void setPluginPackageChecksumExtension(String pluginPackageChecksumExtension) {
    this.pluginPackageChecksumExtension = pluginPackageChecksumExtension == null
        || pluginPackageChecksumExtension.trim().isEmpty()
        ? ".sha256"
        : pluginPackageChecksumExtension;
  }

  public PluginPackageVerificationMode getPluginPackageTrustMode() {
    return productionMode(pluginPackageTrustMode);
  }

  public void setPluginPackageTrustMode(PluginPackageVerificationMode pluginPackageTrustMode) {
    this.pluginPackageTrustMode = pluginPackageTrustMode == null
        ? PluginPackageVerificationMode.DISABLED
        : pluginPackageTrustMode;
  }

  public boolean isPluginPackageSignatureRequired() {
    return productionProfileEnabled || pluginPackageSignatureRequired;
  }

  public void setPluginPackageSignatureRequired(boolean pluginPackageSignatureRequired) {
    this.pluginPackageSignatureRequired = pluginPackageSignatureRequired;
  }

  public boolean isPluginPackageSignatureVerificationRequired() {
    return productionProfileEnabled || pluginPackageSignatureVerificationRequired;
  }

  public void setPluginPackageSignatureVerificationRequired(boolean pluginPackageSignatureVerificationRequired) {
    this.pluginPackageSignatureVerificationRequired = pluginPackageSignatureVerificationRequired;
  }

  public PluginPackageVerificationMode getPluginCapabilityPrecheckMode() {
    return productionMode(pluginCapabilityPrecheckMode);
  }

  public void setPluginCapabilityPrecheckMode(PluginPackageVerificationMode pluginCapabilityPrecheckMode) {
    this.pluginCapabilityPrecheckMode = pluginCapabilityPrecheckMode == null
        ? PluginPackageVerificationMode.DISABLED
        : pluginCapabilityPrecheckMode;
  }

  public PluginPackageVerificationMode getPluginCompatibilityPrecheckMode() {
    return productionMode(pluginCompatibilityPrecheckMode);
  }

  public void setPluginCompatibilityPrecheckMode(PluginPackageVerificationMode pluginCompatibilityPrecheckMode) {
    this.pluginCompatibilityPrecheckMode = pluginCompatibilityPrecheckMode == null
        ? PluginPackageVerificationMode.DISABLED
        : pluginCompatibilityPrecheckMode;
  }

  public String getPluginCompatibilityPf4bootVersion() {
    return pluginCompatibilityPf4bootVersion;
  }

  public void setPluginCompatibilityPf4bootVersion(String pluginCompatibilityPf4bootVersion) {
    this.pluginCompatibilityPf4bootVersion = pluginCompatibilityPf4bootVersion == null
        || pluginCompatibilityPf4bootVersion.trim().isEmpty()
        ? "0.0.0"
        : pluginCompatibilityPf4bootVersion.trim();
  }

  public String getPluginCompatibilitySpringBootVersion() {
    return pluginCompatibilitySpringBootVersion;
  }

  public void setPluginCompatibilitySpringBootVersion(String pluginCompatibilitySpringBootVersion) {
    this.pluginCompatibilitySpringBootVersion = pluginCompatibilitySpringBootVersion == null
        ? ""
        : pluginCompatibilitySpringBootVersion.trim();
  }

  public String getPluginCompatibilityPf4jVersion() {
    return pluginCompatibilityPf4jVersion;
  }

  public void setPluginCompatibilityPf4jVersion(String pluginCompatibilityPf4jVersion) {
    this.pluginCompatibilityPf4jVersion = pluginCompatibilityPf4jVersion == null
        || pluginCompatibilityPf4jVersion.trim().isEmpty()
        ? "3.15.0"
        : pluginCompatibilityPf4jVersion.trim();
  }

  public String getPluginCompatibilityPf4bootPluginVersion() {
    return pluginCompatibilityPf4bootPluginVersion;
  }

  public void setPluginCompatibilityPf4bootPluginVersion(String pluginCompatibilityPf4bootPluginVersion) {
    this.pluginCompatibilityPf4bootPluginVersion = pluginCompatibilityPf4bootPluginVersion == null
        || pluginCompatibilityPf4bootPluginVersion.trim().isEmpty()
        ? "1.7.0"
        : pluginCompatibilityPf4bootPluginVersion.trim();
  }

  public String getPluginCompatibilityJdkVersion() {
    return pluginCompatibilityJdkVersion;
  }

  public void setPluginCompatibilityJdkVersion(String pluginCompatibilityJdkVersion) {
    this.pluginCompatibilityJdkVersion = pluginCompatibilityJdkVersion == null
        || pluginCompatibilityJdkVersion.trim().isEmpty()
        ? "1.8"
        : pluginCompatibilityJdkVersion.trim();
  }

  public String getPluginCompatibilityPackageFormatVersion() {
    return pluginCompatibilityPackageFormatVersion;
  }

  public void setPluginCompatibilityPackageFormatVersion(String pluginCompatibilityPackageFormatVersion) {
    this.pluginCompatibilityPackageFormatVersion = pluginCompatibilityPackageFormatVersion == null
        || pluginCompatibilityPackageFormatVersion.trim().isEmpty()
        ? "1"
        : pluginCompatibilityPackageFormatVersion.trim();
  }

  public boolean isPluginRepositoryEnabled() {
    return pluginRepositoryEnabled;
  }

  public void setPluginRepositoryEnabled(boolean pluginRepositoryEnabled) {
    this.pluginRepositoryEnabled = pluginRepositoryEnabled;
  }

  public String getPluginRepositoryType() {
    return pluginRepositoryType;
  }

  public void setPluginRepositoryType(String pluginRepositoryType) {
    this.pluginRepositoryType = pluginRepositoryType == null || pluginRepositoryType.trim().isEmpty()
        ? "offline-index"
        : pluginRepositoryType.trim();
  }

  public String getPluginRepositoryLocation() {
    return pluginRepositoryLocation;
  }

  public void setPluginRepositoryLocation(String pluginRepositoryLocation) {
    this.pluginRepositoryLocation = pluginRepositoryLocation == null ? "" : pluginRepositoryLocation.trim();
  }

  public PluginPackageVerificationMode getPluginRepositoryTrustMode() {
    return productionMode(pluginRepositoryTrustMode);
  }

  public void setPluginRepositoryTrustMode(PluginPackageVerificationMode pluginRepositoryTrustMode) {
    this.pluginRepositoryTrustMode = pluginRepositoryTrustMode == null
        ? PluginPackageVerificationMode.WARN
        : pluginRepositoryTrustMode;
  }

  public boolean isPluginRepositoryReleaseGateEnabled() {
    return productionProfileEnabled || pluginRepositoryReleaseGateEnabled;
  }

  public void setPluginRepositoryReleaseGateEnabled(boolean pluginRepositoryReleaseGateEnabled) {
    this.pluginRepositoryReleaseGateEnabled = pluginRepositoryReleaseGateEnabled;
  }

  public String getPluginRepositoryReleaseGateAttribute() {
    return pluginRepositoryReleaseGateAttribute;
  }

  public void setPluginRepositoryReleaseGateAttribute(String pluginRepositoryReleaseGateAttribute) {
    this.pluginRepositoryReleaseGateAttribute = pluginRepositoryReleaseGateAttribute == null
        || pluginRepositoryReleaseGateAttribute.trim().isEmpty()
        ? "releaseGate"
        : pluginRepositoryReleaseGateAttribute.trim();
  }

  public String getPluginRepositoryReleaseGateValue() {
    return pluginRepositoryReleaseGateValue;
  }

  public void setPluginRepositoryReleaseGateValue(String pluginRepositoryReleaseGateValue) {
    this.pluginRepositoryReleaseGateValue = pluginRepositoryReleaseGateValue == null
        || pluginRepositoryReleaseGateValue.trim().isEmpty()
        ? "passed"
        : pluginRepositoryReleaseGateValue.trim();
  }

  public String getPluginRepositoryCacheDirectory() {
    return pluginRepositoryCacheDirectory;
  }

  public void setPluginRepositoryCacheDirectory(String pluginRepositoryCacheDirectory) {
    this.pluginRepositoryCacheDirectory = pluginRepositoryCacheDirectory == null
        ? ""
        : pluginRepositoryCacheDirectory.trim();
  }

  public boolean isPluginRepositoryReplaceEnabled() {
    return pluginRepositoryReplaceEnabled;
  }

  public void setPluginRepositoryReplaceEnabled(boolean pluginRepositoryReplaceEnabled) {
    this.pluginRepositoryReplaceEnabled = pluginRepositoryReplaceEnabled;
  }

  public String getPluginPackageTrustManifestExtension() {
    return pluginPackageTrustManifestExtension;
  }

  public void setPluginPackageTrustManifestExtension(String pluginPackageTrustManifestExtension) {
    this.pluginPackageTrustManifestExtension = pluginPackageTrustManifestExtension == null
        || pluginPackageTrustManifestExtension.trim().isEmpty()
        ? ".pf4boot-trust.json"
        : pluginPackageTrustManifestExtension;
  }

  public String[] getPluginPackageTrustRoots() {
    return pluginPackageTrustRoots;
  }

  public void setPluginPackageTrustRoots(String[] pluginPackageTrustRoots) {
    this.pluginPackageTrustRoots = pluginPackageTrustRoots == null ? new String[0] : pluginPackageTrustRoots;
  }

  public Map<String, String> getPluginPackageTrustRootPublicKeys() {
    return pluginPackageTrustRootPublicKeys;
  }

  public void setPluginPackageTrustRootPublicKeys(Map<String, String> pluginPackageTrustRootPublicKeys) {
    this.pluginPackageTrustRootPublicKeys = pluginPackageTrustRootPublicKeys == null
        ? new HashMap<String, String>()
        : new HashMap<>(pluginPackageTrustRootPublicKeys);
  }

  public DynamicBeanConflictPolicy getDynamicBeanConflictPolicy() {
    return dynamicBeanConflictPolicy;
  }

  public void setDynamicBeanConflictPolicy(DynamicBeanConflictPolicy dynamicBeanConflictPolicy) {
    this.dynamicBeanConflictPolicy = dynamicBeanConflictPolicy == null
        ? DynamicBeanConflictPolicy.REJECT
        : dynamicBeanConflictPolicy;
  }

  public long getPluginDrainTimeoutMillis() {
    return pluginDrainTimeoutMillis;
  }

  public void setPluginDrainTimeoutMillis(long pluginDrainTimeoutMillis) {
    this.pluginDrainTimeoutMillis = pluginDrainTimeoutMillis < 0 ? 0 : pluginDrainTimeoutMillis;
  }

  public boolean isAutoStartPlugin() {
    return autoStartPlugin;
  }

  public void setAutoStartPlugin(boolean autoStartPlugin) {
    this.autoStartPlugin = autoStartPlugin;
  }

  public String[] getDisabledPlugins() {
    return disabledPlugins;
  }

  public void setDisabledPlugins(String[] disabledPlugins) {
    this.disabledPlugins = disabledPlugins;
  }

  public String[] getEnabledPlugins() {
    return enabledPlugins;
  }

  public void setEnabledPlugins(String[] enabledPlugins) {
    this.enabledPlugins = enabledPlugins;
  }

  public boolean isExactVersionAllowed() {
    return exactVersionAllowed;
  }

  public void setExactVersionAllowed(boolean exactVersionAllowed) {
    this.exactVersionAllowed = exactVersionAllowed;
  }

  public List<String> getClassesDirectories() {
    return classesDirectories;
  }

  public void setClassesDirectories(List<String> classesDirectories) {
    this.classesDirectories = classesDirectories;
  }

  public List<String> getLibDirectories() {
    return libDirectories;
  }

  public void setLibDirectories(List<String> libDirectories) {
    this.libDirectories = libDirectories;
  }

  public RuntimeMode getRuntimeMode() {
    return runtimeMode;
  }

  public void setRuntimeMode(String runtimeMode) {
    this.runtimeMode = RuntimeMode.byName(runtimeMode);
  }

  public String getPluginsRoot() {
    return pluginsRoot;
  }

  public void setPluginsRoot(String pluginsRoot) {
    this.pluginsRoot = pluginsRoot;
  }

  public Class<PluginLoader> getCustomPluginLoader() {
    return customPluginLoader;
  }

  public void setCustomPluginLoader(Class<PluginLoader> customPluginLoader) {
    this.customPluginLoader = customPluginLoader;
  }

  public String[] getPluginProfiles() {
    return pluginProfiles;
  }

  public void setPluginProfiles(String[] pluginProfiles) {
    this.pluginProfiles = pluginProfiles;
  }

  public Map<String, Object> getPluginProperties() {
    return pluginProperties;
  }

  public void setPluginProperties(Map<String, Object> pluginProperties) {
    this.pluginProperties = pluginProperties;
  }

  public String getSystemVersion() {
    return systemVersion;
  }

  public void setSystemVersion(String systemVersion) {
    this.systemVersion = systemVersion;
  }

  private PluginPackageVerificationMode productionMode(PluginPackageVerificationMode configuredMode) {
    if (productionProfileEnabled) {
      return PluginPackageVerificationMode.ENFORCE;
    }
    return configuredMode;
  }
}
