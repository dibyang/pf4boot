package net.xdob.pf4boot.jpa;

import java.util.Set;

public interface DynamicMetadataSupport {
  void addEntityClasses(Class<?>... entityClasses);
  void removeEntityClasses(Class<?>... entityClasses);
  Set<Class<?>> getAllEntityClasses();
}
