package net.xdob.pf4boot.trust;

import java.security.PublicKey;

/**
 * 插件签名校验根密钥/证书来源 SPI。
 */
public interface PluginTrustRootProvider {

  PublicKey resolveKey(String keyId);
}
