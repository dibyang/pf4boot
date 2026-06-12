package net.xdob.pf4boot;

import net.xdob.pf4boot.trust.PluginTrustRootProvider;

import java.security.PublicKey;

/**
 * 默认根密钥提供器：当前不提供任何密钥，实现阶段只做 manifest 校验。
 */
public class NoopPluginTrustRootProvider implements PluginTrustRootProvider {

  @Override
  public PublicKey resolveKey(String keyId) {
    return null;
  }
}
