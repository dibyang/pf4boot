package net.xdob.sample.saga;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saga/Outbox 示例业务服务。
 *
 * <p>该服务故意使用 JDBC 和显式表结构，便于 smoke 测试直接验证 outbox、inbox 和重试语义。</p>
 */
@Service
public class SagaOutboxService {

  private final JdbcTemplate jdbcTemplate;

  public SagaOutboxService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void initializeSchema() {
    jdbcTemplate.execute("create table if not exists saga_orders ("
        + "order_id varchar(64) primary key, amount decimal(18,2), status varchar(32), created_at bigint)");
    jdbcTemplate.execute("create table if not exists saga_outbox ("
        + "event_id varchar(64) primary key, event_key varchar(128) unique, order_id varchar(64), "
        + "amount decimal(18,2), status varchar(32), attempts int, fail_once boolean, created_at bigint, updated_at bigint)");
    jdbcTemplate.execute("create table if not exists saga_billing_account ("
        + "account_id varchar(64) primary key, charged decimal(18,2))");
    jdbcTemplate.execute("create table if not exists saga_billing_inbox ("
        + "event_key varchar(128) primary key, order_id varchar(64), created_at bigint)");
    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from saga_billing_account where account_id='default'",
        Integer.class);
    if (count == null || count == 0) {
      jdbcTemplate.update("insert into saga_billing_account(account_id, charged) values('default', 0)");
    }
  }

  @Transactional
  public Map<String, Object> createOrder(BigDecimal amount, boolean failBillingOnce) {
    String orderId = "order-" + UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    jdbcTemplate.update(
        "insert into saga_orders(order_id, amount, status, created_at) values(?, ?, 'PENDING', ?)",
        orderId,
        amount,
        now);
    jdbcTemplate.update(
        "insert into saga_outbox(event_id, event_key, order_id, amount, status, attempts, fail_once, created_at, updated_at) "
            + "values(?, ?, ?, ?, 'NEW', 0, ?, ?, ?)",
        "evt-" + UUID.randomUUID().toString(),
        "billing:" + orderId,
        orderId,
        amount,
        failBillingOnce,
        now,
        now);
    return order(orderId);
  }

  @Transactional
  public Map<String, Object> dispatchOne() {
    List<Map<String, Object>> events = jdbcTemplate.queryForList(
        "select * from saga_outbox where status in ('NEW','RETRY') order by created_at limit 1");
    if (events.isEmpty()) {
      return summary("IDLE", null);
    }
    Map<String, Object> event = events.get(0);
    String eventId = (String) event.get("EVENT_ID");
    String eventKey = (String) event.get("EVENT_KEY");
    String orderId = (String) event.get("ORDER_ID");
    BigDecimal amount = (BigDecimal) event.get("AMOUNT");
    int attempts = ((Number) event.get("ATTEMPTS")).intValue();
    boolean failOnce = Boolean.TRUE.equals(event.get("FAIL_ONCE"));
    long now = System.currentTimeMillis();
    if (failOnce && attempts == 0) {
      jdbcTemplate.update(
          "update saga_outbox set status='RETRY', attempts=?, updated_at=? where event_id=?",
          attempts + 1,
          now,
          eventId);
      return summary("RETRY", orderId);
    }
    applyBilling(eventKey, orderId, amount);
    jdbcTemplate.update(
        "update saga_orders set status='PAID' where order_id=?",
        orderId);
    jdbcTemplate.update(
        "update saga_outbox set status='SENT', attempts=?, updated_at=? where event_id=?",
        attempts + 1,
        now,
        eventId);
    return summary("SENT", orderId);
  }

  public Map<String, Object> order(String orderId) {
    return jdbcTemplate.queryForMap("select order_id, amount, status from saga_orders where order_id=?", orderId);
  }

  public Map<String, Object> summary() {
    return summary("SUMMARY", null);
  }

  private void applyBilling(String eventKey, String orderId, BigDecimal amount) {
    try {
      jdbcTemplate.update(
          "insert into saga_billing_inbox(event_key, order_id, created_at) values(?, ?, ?)",
          eventKey,
          orderId,
          System.currentTimeMillis());
    } catch (DuplicateKeyException duplicate) {
      return;
    }
    jdbcTemplate.update(
        "update saga_billing_account set charged=charged+? where account_id='default'",
        amount);
  }

  private Map<String, Object> summary(String dispatchStatus, String orderId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("dispatchStatus", dispatchStatus);
    result.put("orderId", orderId);
    result.put("orders", count("saga_orders"));
    result.put("pendingOutbox", countWhere("saga_outbox", "status in ('NEW','RETRY')"));
    result.put("sentOutbox", countWhere("saga_outbox", "status='SENT'"));
    result.put("inbox", count("saga_billing_inbox"));
    result.put("charged", jdbcTemplate.queryForObject(
        "select charged from saga_billing_account where account_id='default'",
        BigDecimal.class));
    return result;
  }

  private long count(String table) {
    Long count = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    return count == null ? 0L : count;
  }

  private long countWhere(String table, String condition) {
    Long count = jdbcTemplate.queryForObject("select count(*) from " + table + " where " + condition, Long.class);
    return count == null ? 0L : count;
  }
}
