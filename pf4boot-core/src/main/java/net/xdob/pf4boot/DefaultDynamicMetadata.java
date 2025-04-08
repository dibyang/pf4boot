package net.xdob.pf4boot;

import net.xdob.pf4boot.jpa.DynamicMetadata;

import java.util.HashSet;
import java.util.Set;

public class DefaultDynamicMetadata implements DynamicMetadata {

  private final Set<Class<?>>  entityClasses = new HashSet<>();


  @Override
  public void addEntityClasses(Class<?>... entityClasses) {

  }

  @Override
  public void removeEntityClasses(Class<?>... entityClasses) {

  }

  @Override
  public void sync() {

  }
}
