package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新服务。
 */
public interface JpaDomainReloadService extends JpaDomainReloadPlanService {

  /**
   * 执行刷新。实现必须先生成计划并拒绝不可执行计划。
   *
   * @param request 刷新请求
   * @return 刷新记录
   */
  JpaDomainReloadRecord reload(JpaDomainReloadRequest request);

  /**
   * 查询指定刷新记录。
   */
  JpaDomainReloadRecord getRecord(String reloadId);

  /**
   * 查询指定 domain 当前正在执行的刷新记录。
   */
  JpaDomainReloadRecord getCurrent(String domainId);
}
