package net.xdob.pf4boot.jpa.domain.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import javax.persistence.Entity;
import java.io.IOException;

/**
 * 共享 JPA 事务域 entity 包可见性校验。
 */
class JpaDomainEntityPackageValidator {

  private static final Logger LOG = LoggerFactory.getLogger(JpaDomainEntityPackageValidator.class);

  private final ResourceLoader resourceLoader;

  JpaDomainEntityPackageValidator(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  int validate(String domainId, String[] entityPackages) {
    int entityCount = 0;
    for (String entityPackage : entityPackages) {
      assertPackageHasClassResources(domainId, entityPackage);
      entityCount += scanEntityCount(domainId, entityPackage);
    }
    if (entityCount == 0) {
      LOG.warn(
          "[PJF-008] Domain JPA provider {} did not find any @Entity from configured packages {}.",
          domainId, entityPackages);
    }
    return entityCount;
  }

  private void assertPackageHasClassResources(String domainId, String entityPackage) {
    String packagePath = ClassUtils.convertClassNameToResourcePath(entityPackage);
    String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + packagePath + "/**/*.class";
    try {
      Resource[] resources = resolver().getResources(pattern);
      if (resources.length == 0) {
        throw new IllegalStateException(
            "[PJF-008] Domain JPA provider '" + domainId
                + "' cannot resolve entity package '" + entityPackage
                + "' from plugin classpath.");
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          "[PJF-008] Domain JPA provider '" + domainId
              + "' failed to scan entity package '" + entityPackage + "'.",
          e);
    }
  }

  private int scanEntityCount(String domainId, String entityPackage) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.setResourceLoader(this.resourceLoader);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class, false));
    try {
      return scanner.findCandidateComponents(entityPackage).size();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "[PJF-008] Domain JPA provider '" + domainId
              + "' failed to scan @Entity classes from package '" + entityPackage + "'.",
          e);
    }
  }

  private ResourcePatternResolver resolver() {
    if (this.resourceLoader instanceof ResourcePatternResolver) {
      return (ResourcePatternResolver) this.resourceLoader;
    }
    return new PathMatchingResourcePatternResolver(this.resourceLoader);
  }
}
