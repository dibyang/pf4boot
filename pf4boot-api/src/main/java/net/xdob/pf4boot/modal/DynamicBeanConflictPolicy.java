package net.xdob.pf4boot.modal;

/**
 * Strategy used when pf4boot dynamically registers a bean with an existing name.
 */
public enum DynamicBeanConflictPolicy {
  /**
   * Fail fast when a singleton or bean definition with the same name already exists.
   */
  REJECT,

  /**
   * Log the existing registration, destroy/remove it, and register the new bean.
   */
  REPLACE
}
