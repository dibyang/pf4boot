package net.xdob.pf4boot.jpa.reload;

/**
 * 阻止 JPA domain 刷新执行的原因。
 */
public class JpaDomainReloadBlocker {

  private final JpaDomainReloadFailureCode code;
  private final String message;
  private final String subject;

  public JpaDomainReloadBlocker(
      JpaDomainReloadFailureCode code,
      String message,
      String subject) {
    this.code = code;
    this.message = message;
    this.subject = subject;
  }

  public JpaDomainReloadFailureCode getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getSubject() {
    return subject;
  }
}
