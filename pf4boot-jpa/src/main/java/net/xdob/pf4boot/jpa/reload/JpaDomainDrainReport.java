package net.xdob.pf4boot.jpa.reload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA domain 刷新前的 drain 检查结果。
 */
public class JpaDomainDrainReport {

  private final boolean accepted;
  private final JpaDomainReloadFailureCode failureCode;
  private final String message;
  private final List<String> pluginIds;
  private final List<JpaDomainDrainerResult> drainerResults;
  private final long startedAt;
  private final long finishedAt;
  private final List<String> warnings;

  public JpaDomainDrainReport(boolean accepted, String message) {
    this(accepted, null, message, null, null, 0L, 0L, null);
  }

  public JpaDomainDrainReport(
      boolean accepted,
      JpaDomainReloadFailureCode failureCode,
      String message,
      List<String> pluginIds,
      List<JpaDomainDrainerResult> drainerResults,
      long startedAt,
      long finishedAt,
      List<String> warnings) {
    this.accepted = accepted;
    this.failureCode = failureCode;
    this.message = trimMessage(message);
    this.pluginIds = copyStrings(pluginIds);
    this.drainerResults = copy(drainerResults);
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.warnings = copyStrings(warnings);
  }

  public boolean isAccepted() {
    return accepted;
  }

  public JpaDomainReloadFailureCode getFailureCode() {
    return failureCode;
  }

  public String getMessage() {
    return message;
  }

  public List<String> getPluginIds() {
    return pluginIds;
  }

  public List<JpaDomainDrainerResult> getDrainerResults() {
    return drainerResults;
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

  public List<String> getWarnings() {
    return warnings;
  }

  private static List<String> copyStrings(List<String> values) {
    if (values == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(values));
  }

  private static <T> List<T> copy(List<T> values) {
    if (values == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(values));
  }

  private static String trimMessage(String message) {
    if (message == null || message.length() <= 512) {
      return message;
    }
    return message.substring(0, 512);
  }
}
