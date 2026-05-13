package net.xdob.pf4boot.jpa;

/**
 * Records candidate JPA entity classes for a plugin.
 *
 * Runtime synchronization into an already-created EntityManagerFactory is not
 * supported. Implementations must fail clearly from {@link #sync()} instead of
 * silently pretending that Hibernate metadata was refreshed.
 */
public interface DynamicMetadata {
  void addEntityClasses(Class<?>... entityClasses);
  void removeEntityClasses(Class<?>... entityClasses);
  void sync();
}
