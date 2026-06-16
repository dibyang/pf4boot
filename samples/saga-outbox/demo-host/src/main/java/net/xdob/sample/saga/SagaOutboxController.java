package net.xdob.sample.saga;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Saga/Outbox 示例 HTTP API。
 */
@RestController
public class SagaOutboxController {

  private final SagaOutboxService service;

  public SagaOutboxController(SagaOutboxService service) {
    this.service = service;
  }

  @PostMapping("/api/saga/orders")
  public Map<String, Object> createOrder(
      @RequestParam(defaultValue = "10.00") BigDecimal amount,
      @RequestParam(defaultValue = "false") boolean failBillingOnce) {
    return service.createOrder(amount, failBillingOnce);
  }

  @PostMapping("/api/saga/dispatcher/tick")
  public Map<String, Object> dispatchOne() {
    return service.dispatchOne();
  }

  @GetMapping("/api/saga/orders/{orderId}")
  public Map<String, Object> order(@PathVariable String orderId) {
    return service.order(orderId);
  }

  @GetMapping("/api/saga/summary")
  public Map<String, Object> summary() {
    return service.summary();
  }
}
