package net.xdob.pf4boot.deployment;

import net.xdob.pf4boot.DefaultAutoExportMgr;
import net.xdob.pf4boot.DefaultShareBeanMgr;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManagerImpl;
import net.xdob.pf4boot.Pf4bootPluginSupport;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.PluginPackageVerificationMode;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.repository.PluginReleaseRequest;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DependencyResolver;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultPluginDeploymentServiceTest {

  private AnnotationConfigApplicationContext applicationContext;
  private TestPluginManager pluginManager;
  private Path pluginsRoot;

  @Before
  public void setUp() throws Exception {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(
        "metadataReaderFactory",
        MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    applicationContext.refresh();

    pluginsRoot = Files.createTempDirectory("pf4boot-deployment-test");
    pluginManager = new TestPluginManager(applicationContext, pluginsRoot);
  }

  @After
  public void tearDown() {
    if (pluginManager != null) {
      pluginManager.close();
    }
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  public void planReplacementCalculatesImpactScopeAndOrders() throws Exception {
    pluginManager.addResolvedPlugin("base");
    pluginManager.addResolvedPlugin("direct", "base");
    pluginManager.addResolvedPlugin("leaf", "direct");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));

    DeploymentRecord record = service().planReplacement("base", stagedPath);

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertEquals(Arrays.asList("direct", "leaf"), record.getPlan().getAffectedPluginIds());
    assertEquals(Arrays.asList("leaf", "direct", "base"), record.getPlan().getStopOrder());
    assertEquals(Arrays.asList("base", "direct", "leaf"), record.getPlan().getStartOrder());
    assertTrue(record.getPlan().isExecutable());
  }

  @Test
  public void precheckDoesNotMutateRuntimeState() throws Exception {
    pluginManager.addResolvedPlugin("base");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    PluginState before = pluginManager.getPlugin("base").getPluginState();
    int pluginCount = pluginManager.getPlugins().size();

    DeploymentRecord record = service().planReplacement("base", stagedPath);

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertEquals(pluginCount, pluginManager.getPlugins().size());
    assertEquals(before, pluginManager.getPlugin("base").getPluginState());
    assertEquals("base", pluginManager.getPlugin("base").getPluginId());
  }

  @Test
  public void requiredDependencyMissingBlocksPlan() throws Exception {
    pluginManager.addResolvedPlugin("base");
    DefaultPluginDescriptor stagedDescriptor = descriptor("base", "2.0.0");
    stagedDescriptor.addDependency(new PluginDependency("missing"));
    Path stagedPath = stageDescriptor(stagedDescriptor);

    DeploymentRecord record = service().planReplacement("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertFalse(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "REQUIRED_DEPENDENCY_MISSING".equals(result.getCode())));
  }

  @Test
  public void planReplacementReportsMissingDatasourceCapabilityAsWarning() throws Exception {
    pluginManager.addResolvedPlugin("consumer");
    Path stagedPath = stageDescriptor(descriptor("consumer", "2.0.0"));
    writeCapabilityManifest(stagedPath, "consumer", requiresDatasource("orderDs"));
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCapabilityPrecheckMode(PluginPackageVerificationMode.WARN);

    DeploymentRecord record = service(properties).planReplacement("consumer", stagedPath);

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertTrue(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PFC-002".equals(result.getCode())
            && !result.isError()
            && result.getMessage().contains("orderDs")));
  }

  @Test
  public void planReplacementRejectsMissingDatasourceCapabilityInEnforceMode() throws Exception {
    pluginManager.addResolvedPlugin("consumer");
    Path stagedPath = stageDescriptor(descriptor("consumer", "2.0.0"));
    writeCapabilityManifest(stagedPath, "consumer", requiresDatasource("orderDs"));
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCapabilityPrecheckMode(PluginPackageVerificationMode.ENFORCE);

    DeploymentRecord record = service(properties).planReplacement("consumer", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertFalse(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PFC-002".equals(result.getCode())
            && result.isError()
            && result.getMessage().contains("orderDs")));
  }

  @Test
  public void planReplacementAcceptsDatasourceCapabilityFromAnotherPlugin() throws Exception {
    pluginManager.addResolvedPlugin("provider", PluginState.STARTED);
    writeCapabilityManifest(
        pluginManager.getPlugin("provider").getPluginPath(),
        "provider",
        providesDatasource("orderDs"));
    pluginManager.addResolvedPlugin("consumer", PluginState.STARTED, "provider");
    Path stagedPath = stageDescriptor(descriptor("consumer", "2.0.0"));
    writeCapabilityManifest(stagedPath, "consumer", requiresDatasource("orderDs"));
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCapabilityPrecheckMode(PluginPackageVerificationMode.ENFORCE);

    DeploymentRecord record = service(properties).planReplacement("consumer", stagedPath);

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertTrue(record.getPlan().isExecutable());
    assertFalse(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PFC-002".equals(result.getCode())));
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "CAPABILITY_PRECHECK_PASSED".equals(result.getCode())));
  }

  @Test
  public void planReplacementFromRepositoryRelease() throws Exception {
    pluginManager.addResolvedPlugin("base");
    Path repositoryRoot = Files.createTempDirectory("pf4boot-offline-repo");
    Path packagePath = repositoryRoot.resolve("plugins/base-2.0.0.zip");
    Files.createDirectories(packagePath.getParent());
    Files.write(packagePath, "base".getBytes("UTF-8"));
    pluginManager.addStagedDescriptor(packagePath, descriptor("base", "2.0.0"));
    writeRepositoryIndex(repositoryRoot, "base", "2.0.0", "plugins/base-2.0.0.zip", sha256(packagePath));
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginRepositoryEnabled(true);
    properties.setPluginRepositoryLocation(repositoryRoot.toString());

    DeploymentRecord record = service(properties).planReplacement(releaseRequest("base", "2.0.0"));

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertTrue(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PFR-001".equals(result.getCode())));
    assertEquals(1, pluginManager.getPlugins().size());
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
  }

  @Test
  public void repositoryReleaseRecordsSafeSummary() throws Exception {
    pluginManager.addResolvedPlugin("base");
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginRepositoryEnabled(true);
    properties.setPluginRepositoryLocation(pluginsRoot.resolve("missing-repo").toString());

    DeploymentRecord record = service(properties).planReplacement(releaseRequest("base", "2.0.0"));

    assertEquals(DeploymentState.FAILED, record.getState());
    assertFalse(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PFR-003".equals(result.getCode())));
  }

  @Test
  public void planWarnsPf4bootVersionMismatch() throws Exception {
    pluginManager.addResolvedPlugin("base");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    writeCompatibilityManifest(stagedPath, "base", "[9.0,10.0)", null);
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCompatibilityPrecheckMode(PluginPackageVerificationMode.WARN);
    properties.setPluginCompatibilityPf4bootVersion("3.0.0");

    DeploymentRecord record = service(properties).planReplacement("base", stagedPath);

    assertEquals(DeploymentState.PRECHECKED, record.getState());
    assertTrue(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "PF4BOOT_VERSION_RANGE_MISMATCH".equals(result.getCode())
            && !result.isError()));
  }

  @Test
  public void planRejectsSpringBootVersionMismatchInEnforceMode() throws Exception {
    pluginManager.addResolvedPlugin("base");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    writeCompatibilityManifest(stagedPath, "base", null, "[3.0,4.0)");
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCompatibilityPrecheckMode(PluginPackageVerificationMode.ENFORCE);
    properties.setPluginCompatibilitySpringBootVersion("2.7.22");

    DeploymentRecord record = service(properties).planReplacement("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertFalse(record.getPlan().isExecutable());
    assertTrue(record.getPlan().getCheckResults().stream()
        .anyMatch(result -> "SPRING_BOOT_VERSION_RANGE_MISMATCH".equals(result.getCode())
            && result.isError()));
  }

  @Test
  public void replaceStopsUnloadsLoadsAndStartsInOrder() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    pluginManager.addResolvedPlugin("direct", PluginState.STARTED, "base");
    pluginManager.addResolvedPlugin("leaf", PluginState.STARTED, "direct");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.SUCCEEDED, record.getState());
    assertEquals(Arrays.asList(
        DeploymentState.PRECHECKED,
        DeploymentState.APPLYING,
        DeploymentState.DRAINING,
        DeploymentState.STOPPING,
        DeploymentState.CLEANUP_VERIFYING,
        DeploymentState.ACTIVATING,
        DeploymentState.STARTING,
        DeploymentState.VERIFYING,
        DeploymentState.SUCCEEDED), record.getStateHistory());
    assertEquals("2.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
    assertEquals(Arrays.asList(
        "stop:leaf", "stop:direct", "stop:base",
        "unload:leaf", "unload:direct", "unload:base",
        "load:base:2.0.0", "load:direct:1.0.0", "load:leaf:1.0.0",
        "start:base", "start:direct", "start:leaf"), pluginManager.operations);
  }

  @Test
  public void replaceJpaProviderStopsConsumersBeforeProviderAndStartsProviderFirst() throws Exception {
    pluginManager.addResolvedPlugin("sample-demo-jpa-domain", PluginState.STARTED);
    pluginManager.addResolvedPlugin(
        "sample-user-book-service", PluginState.STARTED, "sample-demo-jpa-domain");
    pluginManager.addResolvedPlugin(
        "sample-workflow", PluginState.STARTED,
        "sample-demo-jpa-domain", "sample-user-book-service");
    Path stagedPath = stageDescriptor(descriptor("sample-demo-jpa-domain", "2.0.0"));

    DeploymentRecord record = service().replace("sample-demo-jpa-domain", stagedPath);

    assertEquals(DeploymentState.SUCCEEDED, record.getState());
    assertEquals(Arrays.asList(
        "sample-workflow",
        "sample-user-book-service",
        "sample-demo-jpa-domain"), record.getPlan().getStopOrder());
    assertEquals(Arrays.asList(
        "sample-demo-jpa-domain",
        "sample-user-book-service",
        "sample-workflow"), record.getPlan().getStartOrder());
    assertEquals("2.0.0",
        pluginManager.getPlugin("sample-demo-jpa-domain").getDescriptor().getVersion());
  }

  @Test
  public void replaceRollsBackWhenNewPluginStartFails() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    pluginManager.addResolvedPlugin("direct", PluginState.STARTED, "base");
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    pluginManager.failStartVersion("base", "2.0.0");

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
    assertEquals(PluginState.STARTED, pluginManager.getPlugin("base").getPluginState());
    assertEquals(PluginState.STARTED, pluginManager.getPlugin("direct").getPluginState());
    assertTrue(record.getMessage().contains("rollback succeeded"));
  }

  @Test
  public void replaceRollsBackWhenPackageActivationFails() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    pluginManager.failLoad(stagedPath);

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
    assertEquals(PluginState.STARTED, pluginManager.getPlugin("base").getPluginState());
    assertTrue(record.getMessage().contains("rollback succeeded"));
  }

  @Test
  public void replaceMovesToManualInterventionWhenRollbackFails() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path currentPath = pluginManager.getPlugin("base").getPluginPath();
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    pluginManager.failStartVersion("base", "2.0.0");
    pluginManager.removeStagedDescriptor(currentPath);

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.MANUAL_INTERVENTION, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertEquals("DEPLOYMENT_FAILED", record.getErrorCode());
    assertTrue(record.getMessage().contains("rollback also failed"));
  }

  @Test
  public void replaceRollsBackWhenDrainTimeouts() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    TestDrainer drainer = new TestDrainer(false);

    DeploymentRecord record = service(drainer, null).replace("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertTrue(drainer.endCalled.get());
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
  }

  @Test
  public void replaceRollsBackWhenCleanupVerificationFails() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    PluginCleanupVerifier verifier = (pluginId, classLoader) ->
        Collections.singletonList(DeploymentCheckResult.error("TEST_RESIDUE", "not cleaned"));

    DeploymentRecord record = service(null, verifier).replace("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
    assertTrue(record.getMessage().contains("rollback succeeded"));
  }

  @Test
  public void replaceRunsPluginHealthProbeAndSucceeds() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    pluginManager.healthProbeResult("base", "2.0.0",
        DeploymentCheckResult.info("TEST_HEALTH_OK", "healthy"));

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.SUCCEEDED, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.VERIFYING));
    assertEquals("2.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
  }

  @Test
  public void replaceRollsBackWhenPluginHealthProbeFails() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    pluginManager.healthProbeResult("base", "2.0.0",
        DeploymentCheckResult.error("TEST_HEALTH_FAIL", "unhealthy"));

    DeploymentRecord record = service().replace("base", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertEquals("HEALTH_CHECK_FAILED", record.getErrorCode());
    assertEquals("1.0.0", pluginManager.getPlugin("base").getDescriptor().getVersion());
    assertEquals(PluginState.STARTED, pluginManager.getPlugin("base").getPluginState());
  }

  @Test
  public void replaceJpaProviderRollsBackWhenHealthVerifierFails() throws Exception {
    pluginManager.addResolvedPlugin("sample-demo-jpa-domain", PluginState.STARTED);
    pluginManager.addResolvedPlugin(
        "sample-user-book-service", PluginState.STARTED, "sample-demo-jpa-domain");
    Path stagedPath = stageDescriptor(descriptor("sample-demo-jpa-domain", "2.0.0"));
    PluginHealthVerifier healthVerifier = (context, pluginClassLoader) -> {
      PluginWrapper plugin = pluginManager.getPlugin(context.getPluginId());
      if ("sample-demo-jpa-domain".equals(context.getPluginId())
          && plugin != null
          && "2.0.0".equals(plugin.getDescriptor().getVersion())) {
        return Collections.singletonList(
            DeploymentCheckResult.error("JPA_DATASOURCE_UNAVAILABLE", "domain datasource failed"));
      }
      return Collections.singletonList(
          DeploymentCheckResult.info("JPA_NOT_USED", "not a jpa provider"));
    };

    DeploymentRecord record = service(null, null, healthVerifier, null)
        .replace("sample-demo-jpa-domain", stagedPath);

    assertEquals(DeploymentState.FAILED, record.getState());
    assertEquals("HEALTH_CHECK_FAILED", record.getErrorCode());
    assertTrue(record.getStateHistory().contains(DeploymentState.ROLLING_BACK));
    assertEquals("1.0.0",
        pluginManager.getPlugin("sample-demo-jpa-domain").getDescriptor().getVersion());
    assertEquals(PluginState.STARTED,
        pluginManager.getPlugin("sample-demo-jpa-domain").getPluginState());
    assertEquals(PluginState.STARTED,
        pluginManager.getPlugin("sample-user-book-service").getPluginState());
  }

  @Test
  public void deploymentRecorderTracksMetrics() throws Exception {
    pluginManager.addResolvedPlugin("base", PluginState.STARTED);
    Path stagedPath = stageDescriptor(descriptor("base", "2.0.0"));
    DefaultPluginDeploymentRecorder recorder = new DefaultPluginDeploymentRecorder();

    DeploymentRecord record = service(null, null, recorder).replace("base", stagedPath);

    assertEquals(DeploymentState.SUCCEEDED, record.getState());
    PluginDeploymentMetricsSnapshot snapshot = recorder.snapshot();
    assertEquals(1, snapshot.getDeploymentTotal());
    assertEquals(0, snapshot.getRollbackTotal());
    assertEquals(0, snapshot.getFailedTotal());
    assertTrue(snapshot.getLastDurationMillis() >= 0);
  }

  private DefaultPluginDeploymentService service() {
    return new DefaultPluginDeploymentService(pluginManager, new Pf4bootProperties(), Collections.emptyList());
  }

  private DefaultPluginDeploymentService service(Pf4bootProperties properties) {
    return new DefaultPluginDeploymentService(pluginManager, properties, Collections.emptyList());
  }

  private DefaultPluginDeploymentService service(
      PluginTrafficDrainer drainer,
      PluginCleanupVerifier verifier) {
    return service(drainer, verifier, null);
  }

  private DefaultPluginDeploymentService service(
      PluginTrafficDrainer drainer,
      PluginCleanupVerifier verifier,
      PluginDeploymentRecorder recorder) {
    return service(drainer, verifier, null, recorder);
  }

  private DefaultPluginDeploymentService service(
      PluginTrafficDrainer drainer,
      PluginCleanupVerifier verifier,
      PluginHealthVerifier healthVerifier,
      PluginDeploymentRecorder recorder) {
    List<PluginTrafficDrainer> drainers = drainer == null
        ? Collections.emptyList()
        : Collections.singletonList(drainer);
    List<PluginCleanupVerifier> verifiers = verifier == null
        ? Collections.emptyList()
        : Collections.singletonList(verifier);
    List<PluginHealthVerifier> healthVerifiers = healthVerifier == null
        ? Collections.emptyList()
        : Collections.singletonList(healthVerifier);
    List<PluginDeploymentRecorder> recorders = recorder == null
        ? Collections.emptyList()
        : Collections.singletonList(recorder);
    return new DefaultPluginDeploymentService(
        pluginManager,
        new Pf4bootProperties(),
        Collections.emptyList(),
        drainers,
        verifiers,
        healthVerifiers,
        recorders);
  }

  private Path stageDescriptor(DefaultPluginDescriptor descriptor) throws Exception {
    Path stagedPath = Files.createDirectory(pluginsRoot.resolve(descriptor.getPluginId() + "-staged"));
    pluginManager.addStagedDescriptor(stagedPath, descriptor);
    return stagedPath;
  }

  private PluginReleaseRequest releaseRequest(String pluginId, String version) {
    PluginReleaseRequest request = new PluginReleaseRequest();
    request.setPluginId(pluginId);
    request.setVersion(version);
    return request;
  }

  private void writeRepositoryIndex(
      Path root,
      String pluginId,
      String version,
      String packagePath,
      String sha256) throws Exception {
    String json = "{"
        + "\"schemaVersion\":1,"
        + "\"repositoryId\":\"sample-repo\","
        + "\"generatedAt\":1,"
        + "\"releases\":[{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"version\":\"" + version + "\","
        + "\"packagePath\":\"" + packagePath + "\","
        + "\"packageSha256\":\"" + sha256 + "\","
        + "\"rolloutPolicy\":\"manual\","
        + "\"rollbackCandidate\":true"
        + "}]"
        + "}";
    Files.write(root.resolve("repository-index.json"), json.getBytes("UTF-8"));
  }

  private String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] bytes = digest.digest(Files.readAllBytes(path));
    StringBuilder builder = new StringBuilder();
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }

  private void writeCapabilityManifest(Path pluginPath, String pluginId, String capabilitiesJson) throws Exception {
    String manifest = "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"pluginVersion\":\"1.0.0\","
        + "\"packageSha256\":\"abc\","
        + "\"capabilities\":" + capabilitiesJson
        + "}";
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        manifest.getBytes("UTF-8"));
  }

  private void writeCompatibilityManifest(
      Path pluginPath,
      String pluginId,
      String pf4bootVersionRange,
      String springBootVersionRange) throws Exception {
    StringBuilder manifest = new StringBuilder("{")
        .append("\"pluginId\":\"").append(pluginId).append("\",")
        .append("\"pluginVersion\":\"1.0.0\",")
        .append("\"packageSha256\":\"abc\"");
    if (pf4bootVersionRange != null) {
      manifest.append(",\"pf4bootVersionRange\":\"").append(pf4bootVersionRange).append("\"");
    }
    if (springBootVersionRange != null) {
      manifest.append(",\"springBootVersionRange\":\"").append(springBootVersionRange).append("\"");
    }
    manifest.append("}");
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        manifest.toString().getBytes("UTF-8"));
  }

  private String providesDatasource(String datasource) {
    return "{"
        + "\"provides\":[{"
        + "\"name\":\"jpa.datasource\","
        + "\"version\":\"1\","
        + "\"scope\":\"DATASOURCE\","
        + "\"attributes\":{"
        + "\"datasource\":\"" + datasource + "\","
        + "\"transactionManager\":\"" + datasource + "TransactionManager\""
        + "}"
        + "}],"
        + "\"requires\":[]"
        + "}";
  }

  private String requiresDatasource(String datasource) {
    return "{"
        + "\"provides\":[{"
        + "\"name\":\"jpa.consumer\","
        + "\"version\":\"1\","
        + "\"scope\":\"PLUGIN\","
        + "\"attributes\":{"
        + "\"datasource\":\"" + datasource + "\""
        + "}"
        + "}],"
        + "\"requires\":[{"
        + "\"name\":\"jpa.datasource\","
        + "\"versionRange\":\"[1,2)\","
        + "\"required\":true,"
        + "\"attributes\":{"
        + "\"datasource\":\"" + datasource + "\","
        + "\"entityPackages\":\"com.example." + datasource + ".domain\","
        + "\"repositoryPackages\":\"com.example." + datasource + ".repository\""
        + "}"
        + "}]"
        + "}";
  }

  private DefaultPluginDescriptor descriptor(String pluginId, String version) {
    return new DefaultPluginDescriptor(pluginId, pluginId, PlanPlugin.class.getName(),
        version, "", "test", "Apache-2.0");
  }

  private class TestPluginManager extends Pf4bootPluginManagerImpl {

    private final Map<Path, PluginDescriptor> stagedDescriptors = new HashMap<>();

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot) {
      super(applicationContext, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), pluginsRoot);
    }

    private final List<String> operations = new ArrayList<>();
    private final Set<Path> failLoadPaths = new HashSet<>();
    private final Map<String, String> failStartVersions = new HashMap<>();
    private final Map<String, DeploymentCheckResult> healthProbeResults = new HashMap<>();

    void addResolvedPlugin(String pluginId, String... dependencies) throws Exception {
      addResolvedPlugin(pluginId, PluginState.RESOLVED, dependencies);
    }

    void addResolvedPlugin(String pluginId, PluginState state, String... dependencies) throws Exception {
      Pf4bootPluginWrapper wrapper = createWrapper(descriptor(pluginId, "1.0.0"), dependencies);
      wrapper.setPluginState(state);
      Files.createDirectories(wrapper.getPluginPath());
      stagedDescriptors.put(wrapper.getPluginPath().toAbsolutePath().normalize(), wrapper.getDescriptor());
      plugins.put(pluginId, wrapper);
      pluginClassLoaders.put(pluginId, wrapper.getPluginClassLoader());
      resolvedPlugins.add(wrapper);
      rebuildDependencyResolver();
    }

    void addStagedDescriptor(Path path, PluginDescriptor descriptor) {
      stagedDescriptors.put(path.toAbsolutePath().normalize(), descriptor);
    }

    void removeStagedDescriptor(Path path) {
      stagedDescriptors.remove(path.toAbsolutePath().normalize());
    }

    void failLoad(Path path) {
      failLoadPaths.add(path.toAbsolutePath().normalize());
    }

    void failStartVersion(String pluginId, String version) {
      failStartVersions.put(pluginId, version);
    }

    void healthProbeResult(String pluginId, String version, DeploymentCheckResult result) {
      healthProbeResults.put(pluginId + ":" + version, result);
    }

    void rebuildDependencyResolver() {
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(plugins.values().stream()
          .map(PluginWrapper::getDescriptor)
          .collect(Collectors.toList()));
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
      return new PluginDescriptorFinder() {
        @Override
        public boolean isApplicable(Path pluginPath) {
          return true;
        }

        @Override
        public PluginDescriptor find(Path pluginPath) {
          return stagedDescriptors.get(pluginPath.toAbsolutePath().normalize());
        }
      };
    }

    @Override
    public String loadPlugin(Path pluginPath) {
      Path normalizedPath = pluginPath.toAbsolutePath().normalize();
      if (failLoadPaths.contains(normalizedPath)) {
        throw new IllegalStateException("load failed: " + pluginPath);
      }
      PluginDescriptor descriptor = stagedDescriptors.get(normalizedPath);
      if (descriptor == null) {
        throw new IllegalStateException("descriptor not found: " + pluginPath);
      }
      Pf4bootPluginWrapper wrapper = createWrapper((DefaultPluginDescriptor) descriptor);
      wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(descriptor.getPluginId(), wrapper);
      pluginClassLoaders.put(descriptor.getPluginId(), wrapper.getPluginClassLoader());
      if (!resolvedPlugins.contains(wrapper)) {
        resolvedPlugins.add(wrapper);
      }
      rebuildDependencyResolver();
      operations.add("load:" + descriptor.getPluginId() + ":" + descriptor.getVersion());
      return descriptor.getPluginId();
    }

    @Override
    public PluginState startPlugin(String pluginId) {
      Pf4bootPluginWrapper wrapper = (Pf4bootPluginWrapper) getPlugin(pluginId);
      if (wrapper == null) {
        throw new IllegalStateException("plugin not loaded: " + pluginId);
      }
      String failVersion = failStartVersions.get(pluginId);
      if (failVersion != null && failVersion.equals(wrapper.getDescriptor().getVersion())) {
        wrapper.setPluginState(PluginState.FAILED);
        operations.add("start-failed:" + pluginId);
        return PluginState.FAILED;
      }
      wrapper.setPluginState(PluginState.STARTED);
      attachHealthProbeContext(wrapper);
      operations.add("start:" + pluginId);
      return PluginState.STARTED;
    }

    @Override
    public PluginState stopPlugin(String pluginId) {
      Pf4bootPluginWrapper wrapper = (Pf4bootPluginWrapper) getPlugin(pluginId);
      if (wrapper == null) {
        return PluginState.UNLOADED;
      }
      wrapper.setPluginState(PluginState.STOPPED);
      if (wrapper.getPlugin() instanceof PlanPlugin) {
        ((PlanPlugin) wrapper.getPlugin()).attachContext(null);
      }
      operations.add("stop:" + pluginId);
      return PluginState.STOPPED;
    }

    @Override
    public boolean unloadPlugin(String pluginId) {
      Pf4bootPluginWrapper wrapper = (Pf4bootPluginWrapper) getPlugin(pluginId);
      if (wrapper == null) {
        return false;
      }
      wrapper.setPluginState(PluginState.UNLOADED);
      plugins.remove(pluginId);
      resolvedPlugins.remove(wrapper);
      unresolvedPlugins.remove(wrapper);
      pluginClassLoaders.remove(pluginId);
      rebuildDependencyResolver();
      operations.add("unload:" + pluginId);
      return true;
    }

    private void attachHealthProbeContext(Pf4bootPluginWrapper wrapper) {
      String key = wrapper.getPluginId() + ":" + wrapper.getDescriptor().getVersion();
      DeploymentCheckResult result = healthProbeResults.get(key);
      if (!(wrapper.getPlugin() instanceof PlanPlugin)) {
        return;
      }
      if (result == null) {
        ((PlanPlugin) wrapper.getPlugin()).attachContext(null);
        return;
      }
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.registerBean("testHealthProbe", PluginHealthProbe.class,
          () -> healthContext -> result);
      context.refresh();
      ((PlanPlugin) wrapper.getPlugin()).attachContext(context);
    }

    @Override
    public void close() {
      plugins.clear();
      resolvedPlugins.clear();
      unresolvedPlugins.clear();
      pluginClassLoaders.clear();
    }

    private Pf4bootPluginWrapper createWrapper(DefaultPluginDescriptor descriptor, String... dependencies) {
      for (String dependency : dependencies) {
        descriptor.addDependency(new PluginDependency(dependency));
      }
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
          this, descriptor, getPluginsRoot().resolve(descriptor.getPluginId()), PlanPlugin.class.getClassLoader());
      wrapper.setPluginFactory(getPluginFactory());
      wrapper.setPluginState(PluginState.RESOLVED);
      return wrapper;
    }
  }

  @PluginStarter(PlanPluginConfig.class)
  public static class PlanPlugin extends Pf4bootPlugin {

    public PlanPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }

    void attachContext(AnnotationConfigApplicationContext context) {
      if (this.pluginContext != null) {
        this.pluginContext.close();
      }
      this.pluginContext = context;
    }
  }

  public static class PlanPluginConfig {
  }

  private static class TestDrainer implements PluginTrafficDrainer {
    private final boolean drainResult;
    private final AtomicBoolean endCalled = new AtomicBoolean(false);

    private TestDrainer(boolean drainResult) {
      this.drainResult = drainResult;
    }

    @Override
    public void beginDrain(java.util.Collection<String> pluginIds) {
    }

    @Override
    public boolean awaitDrain(java.util.Collection<String> pluginIds, long timeoutMillis) {
      return drainResult;
    }

    @Override
    public void endDrain(java.util.Collection<String> pluginIds) {
      endCalled.set(true);
    }
  }
}
