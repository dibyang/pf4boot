package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新异常。
 */
public class JpaDomainReloadException extends RuntimeException {

  private final JpaDomainReloadFailureCode failureCode;

  public JpaDomainReloadException(JpaDomainReloadFailureCode failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public JpaDomainReloadFailureCode getFailureCode() {
    return failureCode;
  }
}
