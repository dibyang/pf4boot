package net.xdob.pf4boot.jpa;

public interface DynamicMetadata {
  void addEntityClasses(Class<?>... entityClasses);
  void removeEntityClasses(Class<?>... entityClasses);
  void sync();
}
