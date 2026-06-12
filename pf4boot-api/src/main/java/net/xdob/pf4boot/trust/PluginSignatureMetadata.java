package net.xdob.pf4boot.trust;

/**
 * 插件签名元数据。
 */
public class PluginSignatureMetadata {

  private String algorithm;
  private String keyId;
  private String value;
  private String certificateChain;

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getCertificateChain() {
    return certificateChain;
  }

  public void setCertificateChain(String certificateChain) {
    this.certificateChain = certificateChain;
  }
}
