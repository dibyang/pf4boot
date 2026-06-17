package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.starter.Pf4bootJpaGovernanceProperties;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 合并 JPA reload 的新旧配置前缀。
 */
final class JpaDomainReloadPropertiesResolver {

  private static final Logger LOG = LoggerFactory.getLogger(JpaDomainReloadPropertiesResolver.class);

  private static final String NEW_PREFIX = "spring.pf4boot.jpa.reload";
  private static final String OLD_PREFIX = "pf4boot.plugin.jpa.domain-reload";

  private JpaDomainReloadPropertiesResolver() {
  }

  static Pf4bootJpaProperties effective(
      Pf4bootJpaProperties legacy,
      Pf4bootJpaGovernanceProperties governance,
      Environment environment) {
    Pf4bootJpaProperties effective = legacy == null ? new Pf4bootJpaProperties() : legacy;
    if (hasAny(environment, NEW_PREFIX)) {
      effective.setDomainReload(governance == null
          ? new Pf4bootJpaProperties.DomainReload()
          : governance.getReload());
      if (hasAny(environment, OLD_PREFIX)) {
        LOG.warn("[PF4BOOT-JPA] Both '{}' and deprecated '{}' are configured. '{}' takes precedence.",
            NEW_PREFIX, OLD_PREFIX, NEW_PREFIX);
      }
      return effective;
    }
    if (hasAny(environment, OLD_PREFIX)) {
      LOG.warn("[PF4BOOT-JPA] Deprecated JPA reload configuration '{}' is used. "
          + "Please migrate host governance configuration to '{}'.", OLD_PREFIX, NEW_PREFIX);
    }
    return effective;
  }

  private static boolean hasAny(Environment environment, String prefix) {
    if (environment == null) {
      return false;
    }
    return StringUtils.hasText(environment.getProperty(prefix + ".mode"))
        || StringUtils.hasText(environment.getProperty(prefix + ".require-idempotency-key"))
        || StringUtils.hasText(environment.getProperty(prefix + ".default-drain-timeout"))
        || StringUtils.hasText(environment.getProperty(prefix + ".default-health-check-timeout"))
        || StringUtils.hasText(environment.getProperty(prefix + ".allow-inferred-consumers"))
        || StringUtils.hasText(environment.getProperty(prefix + ".max-recent-records"))
        || StringUtils.hasText(environment.getProperty(prefix + ".require-drainer"))
        || StringUtils.hasText(environment.getProperty(prefix + ".drain-end-on-failure"))
        || StringUtils.hasText(environment.getProperty(prefix + ".record-store.type"))
        || StringUtils.hasText(environment.getProperty(prefix + ".record-store.directory"))
        || StringUtils.hasText(environment.getProperty(prefix + ".record-store.fail-closed"));
  }
}
