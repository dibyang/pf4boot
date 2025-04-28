package net.xdob.pf4boot.modal;

import java.util.HashMap;
import java.util.Map;

public class SharingBeans {
  private final Map<String, Object> rootBeans = new HashMap<>();
  private final Map<String, Object> appBeans = new HashMap<>();
  private final Map<String, Object> platformBeans = new HashMap<>();

  public Map<String, Object> getRootBeans() {
    return rootBeans;
  }

  public Map<String, Object> getAppBeans() {
    return appBeans;
  }

  public Map<String, Object> getPlatformBeans() {
    return platformBeans;
  }
}
