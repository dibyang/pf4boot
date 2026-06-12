package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PluginManagementRateLimiterTest {

  @Test
  public void validateWriteAllowsUpToLimit() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getRateLimit().setEnabled(true);
    properties.getRateLimit().setWritesPerMinute(2);

    PluginManagementRateLimiter limiter = new PluginManagementRateLimiter(properties);

    limiter.validateWrite("127.0.0.1");
    limiter.validateWrite("127.0.0.1");
  }

  @Test
  public void validateWriteThrowsAfterLimitExceeded() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getRateLimit().setEnabled(true);
    properties.getRateLimit().setWritesPerMinute(1);

    PluginManagementRateLimiter limiter = new PluginManagementRateLimiter(properties);

    limiter.validateWrite("127.0.0.1");
    try {
      limiter.validateWrite("127.0.0.1");
      fail("Expected rate-limit exception");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.RATE_LIMITED, e.getCode());
      assertEquals(429, e.getStatusCode());
    }
  }

  @Test
  public void validateWriteNotApplyWhenDisabled() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.getRateLimit().setEnabled(false);
    properties.getRateLimit().setWritesPerMinute(1);

    PluginManagementRateLimiter limiter = new PluginManagementRateLimiter(properties);

    limiter.validateWrite("127.0.0.1");
    limiter.validateWrite("127.0.0.1");
    limiter.validateWrite("127.0.0.1");
  }
}
