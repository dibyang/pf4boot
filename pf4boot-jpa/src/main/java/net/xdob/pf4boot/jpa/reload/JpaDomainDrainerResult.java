package net.xdob.pf4boot.jpa.reload;

/**
 * 单个 drainer 在某个阶段的执行结果。
 */
public class JpaDomainDrainerResult {

  private final String drainerName;
  private final JpaDomainDrainerPhase phase;
  private final boolean accepted;
  private final long startedAt;
  private final long finishedAt;
  private final String message;

  public JpaDomainDrainerResult(
      String drainerName,
      JpaDomainDrainerPhase phase,
      boolean accepted,
      long startedAt,
      long finishedAt,
      String message) {
    this.drainerName = hasText(drainerName) ? drainerName : "unknown";
    this.phase = phase;
    this.accepted = accepted;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.message = trimMessage(message);
  }

  public String getDrainerName() {
    return drainerName;
  }

  public JpaDomainDrainerPhase getPhase() {
    return phase;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public long getStartedAt() {
    return startedAt;
  }

  public long getFinishedAt() {
    return finishedAt;
  }

  public long getDurationMillis() {
    return Math.max(0L, finishedAt - startedAt);
  }

  public String getMessage() {
    return message;
  }

  private static String trimMessage(String message) {
    if (message == null || message.length() <= 512) {
      return message;
    }
    return message.substring(0, 512);
  }

  private static boolean hasText(String value) {
    return value != null && value.trim().length() > 0;
  }
}
