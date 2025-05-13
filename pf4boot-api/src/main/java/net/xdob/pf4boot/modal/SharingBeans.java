package net.xdob.pf4boot.modal;

import net.xdob.pf4boot.annotation.PluginStarter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SharingBeans {
  private final Map<String,SharingBean> beans = new ConcurrentHashMap<>();

  public SharingBean add(String beanName, Object bean, SharingScope scope, String group) {
    SharingBean sharingBean = SharingBean.of(beanName, bean, scope, group);
    beans.put(beanName, sharingBean);
    return sharingBean;
  }


  public List<SharingBean> getRootBeans() {
    return beans.values().stream()
      .filter(bean -> bean.getScope() == SharingScope.ROOT)
      .collect(Collectors.toList());
  }

  public List<SharingBean> getAppBeans() {
    return beans.values().stream()
      .filter(bean -> bean.getScope() == SharingScope.APPLICATION)
      .collect(Collectors.toList());
  }

  public List<SharingBean> getPlatformBeans() {
    return beans.values().stream()
      .filter(bean -> bean.getScope() == SharingScope.PLATFORM)
      .collect(Collectors.toList());
  }
}
