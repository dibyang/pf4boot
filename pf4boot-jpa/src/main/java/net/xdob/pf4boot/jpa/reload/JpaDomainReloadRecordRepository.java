package net.xdob.pf4boot.jpa.reload;

import java.util.Collections;
import java.util.List;

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

  /**
   * 查询最近的刷新记录，按新到旧排序。
   */
  default List<JpaDomainReloadRecord> recent(int limit) {
    return Collections.emptyList();
  }

  /**
   * 扫描宿主重启后需要人工确认或恢复的刷新记录，按旧到新排序。
   */
  default List<JpaDomainReloadRecord> scanRecoverableRecords() {
    return Collections.emptyList();
  }
}
