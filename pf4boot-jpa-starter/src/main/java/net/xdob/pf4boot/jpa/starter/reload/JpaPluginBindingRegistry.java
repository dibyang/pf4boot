package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.starter.JpaPluginBinding;

import java.util.List;
import java.util.Map;

/**
 * 共享 JPA consumer 绑定注册表。
 *
 * <p>插件上下文启动成功后写入，停止或上下文销毁时移除。实现必须线程安全，
 * 供 host 侧 PLAN_ONLY 服务读取稳定快照。</p>
 */
public interface JpaPluginBindingRegistry {

  /**
   * 注册当前插件的 JPA 绑定。
   */
  void register(JpaPluginBinding binding);

  /**
   * 移除插件绑定。
   */
  void remove(String pluginId);

  /**
   * 查询单个插件绑定。
   */
  JpaPluginBinding findByPluginId(String pluginId);

  /**
   * 查询绑定到指定 domain 的插件。
   */
  List<JpaPluginBinding> findByDomainId(String domainId);

  /**
   * 返回不可变快照。
   */
  Map<String, JpaPluginBinding> snapshot();
}
