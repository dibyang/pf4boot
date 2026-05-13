package net.xdob.pf4boot.jpa;

import java.util.Set;

/**
 * Read side of dynamic metadata candidates.
 *
 * The returned classes are not synchronized into a running JPA metamodel.
 * Plugin JPA entity discovery is currently bounded to startup-time scanning.
 */
public interface DynamicMetadataSupport {
  void addEntityClasses(Class<?>... entityClasses);
  void removeEntityClasses(Class<?>... entityClasses);
  Set<Class<?>> getAllEntityClasses();
}
