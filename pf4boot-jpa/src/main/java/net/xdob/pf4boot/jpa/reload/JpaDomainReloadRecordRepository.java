package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新记录仓库。
 */
public interface JpaDomainReloadRecordRepository {

  void save(JpaDomainReloadRecord record);

  JpaDomainReloadRecord findById(String reloadId);

  JpaDomainReloadRecord findByIdempotencyKey(String idempotencyKey);

  void bindIdempotencyKey(String idempotencyKey, String reloadId);

  /**
   * 查询最近一次刷新记录。
   */
  default JpaDomainReloadRecord findLatest() {
    return null;
  }
}
