package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import net.xdob.pf4boot.trust.PluginPackageTrustRequest;
import net.xdob.pf4boot.trust.PluginPackageTrustResult;
import net.xdob.pf4boot.trust.PluginPackageTrustStatus;
import net.xdob.pf4boot.trust.PluginPackageTrustVerifier;
import net.xdob.pf4boot.trust.PluginSignatureMetadata;
import net.xdob.pf4boot.trust.PluginTrustManifest;
import net.xdob.pf4boot.trust.PluginTrustManifestLoader;
import net.xdob.pf4boot.trust.PluginTrustRootProvider;
import org.pf4j.PluginDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 默认插件包信任清单校验器。
 *
 * <p>该校验器在插件 ClassLoader 创建前执行，只读取插件包旁路
 * {@code .pf4boot-trust.json} 清单并校验 descriptor、SHA-256 和签名元数据是否能
 * 被当前宿主信任配置解释。第一阶段不绑定 CA/KMS 或具体签名体系，签名根密钥通过
 * {@link PluginTrustRootProvider} 预留扩展点。</p>
 */
public class DefaultPluginPackageTrustVerifier implements PluginPackageVerifier {

  private static final int BUFFER_SIZE = 8192;
  private static final String DEFAULT_MANIFEST_EXTENSION = ".pf4boot-trust.json";

  private final Pf4bootProperties properties;
  private final PluginPackageTrustVerifier delegate;
  private final PluginTrustManifestLoader manifestLoader;
  private final List<PluginTrustRootProvider> rootProviders;

  public DefaultPluginPackageTrustVerifier(Pf4bootProperties properties) {
    this(properties, new DefaultPluginTrustManifestLoader(), Collections.<PluginTrustRootProvider>emptyList());
  }

  public DefaultPluginPackageTrustVerifier(
      Pf4bootProperties properties,
      PluginTrustManifestLoader manifestLoader) {
    this(properties, manifestLoader, Collections.<PluginTrustRootProvider>emptyList());
  }

  public DefaultPluginPackageTrustVerifier(
      Pf4bootProperties properties,
      PluginPackageTrustVerifier delegate) {
    this.properties = properties == null ? new Pf4bootProperties() : properties;
    this.delegate = delegate;
    this.manifestLoader = new DefaultPluginTrustManifestLoader();
    this.rootProviders = Collections.<PluginTrustRootProvider>emptyList();
  }

  public DefaultPluginPackageTrustVerifier(
      Pf4bootProperties properties,
      PluginTrustManifestLoader manifestLoader,
      List<PluginTrustRootProvider> rootProviders) {
    this.properties = properties == null ? new Pf4bootProperties() : properties;
    this.delegate = null;
    this.manifestLoader = manifestLoader == null ? new DefaultPluginTrustManifestLoader() : manifestLoader;
    this.rootProviders = immutableRootProviders(rootProviders);
  }

  @Override
  public PluginPackageVerificationResult verify(Path pluginPath, PluginDescriptor pluginDescriptor) {
    PluginPackageVerificationMode mode = properties.getPluginPackageTrustMode();
    if (mode == null || PluginPackageVerificationMode.DISABLED.equals(mode)) {
      return PluginPackageVerificationResult.ok();
    }
    if (pluginPath == null || Files.isDirectory(pluginPath)) {
      return PluginPackageVerificationResult.ok();
    }
    if (delegate != null) {
      return translateTrustResult(mode,
          delegate.verify(new PluginPackageTrustRequest(pluginPath, pluginDescriptor)));
    }
    return verifyManifest(mode, pluginPath, pluginDescriptor);
  }

  private PluginPackageVerificationResult verifyManifest(
      PluginPackageVerificationMode mode,
      Path pluginPath,
      PluginDescriptor descriptor) {
    PluginTrustManifest manifest;
    try {
      manifest = manifestLoader.load(pluginPath, properties.getPluginPackageTrustManifestExtension());
    } catch (RuntimeException e) {
      return result(mode, "PFT-001 Trust manifest parse failed: " + safeMessage(e));
    }
    if (manifest == null) {
      return result(mode, "PFT-001 Trust manifest file not found: " + trustManifestName(pluginPath));
    }
    PluginPackageVerificationResult descriptorResult = verifyDescriptor(mode, descriptor, manifest);
    if (!descriptorResult.isValid() || descriptorResult.isWarning()) {
      return descriptorResult;
    }
    PluginPackageVerificationResult checksumResult = verifyChecksum(mode, pluginPath, manifest);
    if (!checksumResult.isValid() || checksumResult.isWarning()) {
      return checksumResult;
    }
    PluginPackageVerificationResult signatureResult = verifySignatureMetadata(mode, manifest.getSignature());
    if (!signatureResult.isValid() || signatureResult.isWarning()) {
      return signatureResult;
    }
    return PluginPackageVerificationResult.ok();
  }

  private PluginPackageVerificationResult verifyDescriptor(
      PluginPackageVerificationMode mode,
      PluginDescriptor descriptor,
      PluginTrustManifest manifest) {
    if (descriptor == null) {
      return result(mode, "PFT-001 Plugin descriptor unavailable for trust check");
    }
    if (isBlank(manifest.getPluginId())) {
      return result(mode, "PFT-001 Trust manifest pluginId is missing");
    }
    if (descriptor.getPluginId() != null && !descriptor.getPluginId().equals(manifest.getPluginId())) {
      return result(mode, "PFT-001 Trust manifest pluginId mismatch");
    }
    if (isBlank(manifest.getPluginVersion())) {
      return result(mode, "PFT-001 Trust manifest pluginVersion is missing");
    }
    if (descriptor.getVersion() != null && !descriptor.getVersion().equals(manifest.getPluginVersion())) {
      return result(mode, "PFT-001 Trust manifest pluginVersion mismatch");
    }
    return PluginPackageVerificationResult.ok();
  }

  private PluginPackageVerificationResult verifyChecksum(
      PluginPackageVerificationMode mode,
      Path pluginPath,
      PluginTrustManifest manifest) {
    if (isBlank(manifest.getPackageSha256())) {
      return result(mode, "PFT-002 Trust manifest packageSha256 is missing");
    }
    try {
      String expected = manifest.getPackageSha256().trim().toLowerCase(Locale.ENGLISH);
      String actual = sha256(pluginPath);
      if (!expected.equals(actual)) {
        return result(mode, "PFT-002 Trust manifest packageSha256 mismatch");
      }
      return PluginPackageVerificationResult.ok();
    } catch (RuntimeException e) {
      return result(mode, "PFT-002 Trust manifest SHA-256 verification failed: " + safeMessage(e));
    }
  }

  private PluginPackageVerificationResult verifySignatureMetadata(
      PluginPackageVerificationMode mode,
      PluginSignatureMetadata signature) {
    if (signature == null || (isBlank(signature.getAlgorithm())
        && isBlank(signature.getKeyId()) && isBlank(signature.getValue()))) {
      return PluginPackageVerificationResult.ok();
    }
    if (isBlank(signature.getAlgorithm()) || isBlank(signature.getKeyId()) || isBlank(signature.getValue())) {
      return result(mode, "PFT-003 Trust manifest signature metadata is incomplete");
    }
    if (!hasTrustedKey(signature.getKeyId())) {
      return result(mode, "PFT-004 Trust root not configured for signature keyId: " + signature.getKeyId());
    }
    return PluginPackageVerificationResult.ok();
  }

  private boolean hasTrustedKey(String keyId) {
    for (PluginTrustRootProvider provider : rootProviders) {
      if (provider == null) {
        continue;
      }
      PublicKey publicKey = provider.resolveKey(keyId);
      if (publicKey != null) {
        return true;
      }
    }
    return false;
  }

  private PluginPackageVerificationResult translateTrustResult(
      PluginPackageVerificationMode mode,
      PluginPackageTrustResult result) {
    if (result == null || result.getStatus() == null || PluginPackageTrustStatus.OK.equals(result.getStatus())) {
      return PluginPackageVerificationResult.ok();
    }
    if (PluginPackageTrustStatus.WARN.equals(result.getStatus())) {
      return PluginPackageVerificationResult.warn(result.getMessage());
    }
    return result(mode, result.getMessage());
  }

  private PluginPackageVerificationResult result(PluginPackageVerificationMode mode, String message) {
    if (PluginPackageVerificationMode.ENFORCE.equals(mode)) {
      return PluginPackageVerificationResult.fail(message);
    }
    return PluginPackageVerificationResult.warn(message);
  }

  private String sha256(Path pluginPath) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
    try (InputStream inputStream = Files.newInputStream(pluginPath)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Read plugin package for SHA-256 failed", e);
    }
    return toHex(digest.digest());
  }

  private String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      String hex = Integer.toHexString(value & 0xff);
      if (hex.length() == 1) {
        builder.append('0');
      }
      builder.append(hex);
    }
    return builder.toString();
  }

  private String trustManifestName(Path pluginPath) {
    String extension = properties.getPluginPackageTrustManifestExtension();
    if (isBlank(extension)) {
      extension = DEFAULT_MANIFEST_EXTENSION;
    }
    return pluginPath.getFileName().toString() + extension;
  }

  private String safeMessage(RuntimeException e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private List<PluginTrustRootProvider> immutableRootProviders(List<PluginTrustRootProvider> providers) {
    if (providers == null || providers.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(providers));
  }
}
