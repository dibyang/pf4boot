package org.springframework.core.io.support;

public class SpringFactoriesLoaderHelp {
  public static void clearCache() {
    SpringFactoriesLoader.cache.clear();
  }
}
