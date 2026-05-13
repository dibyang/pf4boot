package net.xdob.pf4boot;

import net.xdob.pf4boot.jpa.DynamicMetadata;
import net.xdob.pf4boot.jpa.DynamicMetadataSupport;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDynamicMetadata implements DynamicMetadata, DynamicMetadataSupport {

  private final Set<Class<?>> entityClasses =
      Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());


  @Override
  public void addEntityClasses(Class<?>... entityClasses) {
    if (entityClasses != null) {
      this.entityClasses.addAll(Arrays.asList(entityClasses));
    }
  }

  @Override
  public void removeEntityClasses(Class<?>... entityClasses) {
    if (entityClasses != null) {
      this.entityClasses.removeAll(Arrays.asList(entityClasses));
    }
  }

  @Override
  public Set<Class<?>> getAllEntityClasses() {
    return Collections.unmodifiableSet(new HashSet<>(entityClasses));
  }

  @Override
  public void sync() {
    throw new UnsupportedOperationException(
        "Runtime JPA metadata synchronization is not supported. " +
            "Plugin entity classes are discovered when the EntityManagerFactory is created.");
  }
}
