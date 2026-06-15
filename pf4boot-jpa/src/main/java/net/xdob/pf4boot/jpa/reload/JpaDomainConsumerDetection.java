package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain consumer 的识别来源。
 */
public enum JpaDomainConsumerDetection {
  EXACT_BINDING,
  CAPABILITY_DECLARED,
  INFERRED_DEPENDENCY
}
