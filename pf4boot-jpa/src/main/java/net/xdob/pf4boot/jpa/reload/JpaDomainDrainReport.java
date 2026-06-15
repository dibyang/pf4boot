package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新前的 drain 检查结果。
 */
public class JpaDomainDrainReport {

  private final boolean accepted;
  private final String message;

  public JpaDomainDrainReport(boolean accepted, String message) {
    this.accepted = accepted;
    this.message = message;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getMessage() {
    return message;
  }
}
