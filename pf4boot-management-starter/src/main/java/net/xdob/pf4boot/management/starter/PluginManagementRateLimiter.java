package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple fixed-window rate limiter for write operations.
 * The limiter is intentionally lightweight and memory based for stage one.
 */
public class PluginManagementRateLimiter {

  private static final long WINDOW_MILLIS = 60_000L;

  private final Pf4bootManagementProperties properties;
  private final Map<String, RateWindow> windows = new ConcurrentHashMap<String, RateWindow>();

  public PluginManagementRateLimiter(Pf4bootManagementProperties properties) {
    this.properties = properties;
  }

  public void validateWrite(String subjectKey) {
    // Fixed-window memory limiter: reject burst traffic when writes per minute exceeds threshold.
    if (!properties.getRateLimit().isEnabled()) {
      return;
    }
    int writesPerMinute = properties.getRateLimit().getWritesPerMinute();
    if (writesPerMinute <= 0) {
      return;
    }
    long now = System.currentTimeMillis();
    String key = subjectKey == null || subjectKey.trim().length() == 0 ? "anonymous" : subjectKey;
    RateWindow window = windows.get(key);
    if (window == null) {
      window = new RateWindow(now, 0);
      RateWindow existing = windows.putIfAbsent(key, window);
      if (existing != null) {
        window = existing;
      }
    }
    synchronized (window) {
      if (window.expired(now)) {
        window.reset(now);
      }
      if (window.count >= writesPerMinute) {
        throw new PluginManagementException(
            PluginManagementErrorCode.RATE_LIMITED,
            "Write request rate limit exceeded",
            429);
      }
      window.count++;
    }
  }

  private static class RateWindow {
    private long windowStartAt;
    private int count;

    private RateWindow(long now, int count) {
      this.windowStartAt = now;
      this.count = count;
    }

    private boolean expired(long now) {
      return now - windowStartAt >= WINDOW_MILLIS;
    }

    private void reset(long now) {
      this.windowStartAt = now;
      this.count = 0;
    }
  }
}
