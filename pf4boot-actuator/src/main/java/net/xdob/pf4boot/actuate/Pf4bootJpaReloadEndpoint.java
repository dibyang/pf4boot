package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA domain 刷新只读观测端点。
 */
@Endpoint(id = "pf4bootjpareload")
public class Pf4bootJpaReloadEndpoint {

  private final JpaDomainReloadPlanService planService;
  private final JpaDomainReloadService reloadService;

  public Pf4bootJpaReloadEndpoint(
      JpaDomainReloadPlanService planService,
      JpaDomainReloadService reloadService) {
    this.planService = planService;
    this.reloadService = reloadService;
  }

  /**
   * 返回 JPA domain 刷新能力摘要。
   */
  @ReadOperation
  public Map<String, Object> summary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("planAvailable", planService != null);
    summary.put("executeAvailable", reloadService != null);
    summary.put("mode", reloadService == null ? "PLAN_ONLY_OR_DISABLED" : "EXECUTE_SERVICE_PRESENT");
    return summary;
  }
}
