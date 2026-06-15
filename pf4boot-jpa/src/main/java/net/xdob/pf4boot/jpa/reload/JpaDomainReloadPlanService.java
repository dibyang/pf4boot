package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新计划服务。
 */
public interface JpaDomainReloadPlanService {

  /**
   * 生成刷新计划，不允许产生插件 stop/start/reload 副作用。
   *
   * @param request 刷新请求
   * @return 刷新计划
   */
  JpaDomainReloadPlan plan(JpaDomainReloadRequest request);
}
