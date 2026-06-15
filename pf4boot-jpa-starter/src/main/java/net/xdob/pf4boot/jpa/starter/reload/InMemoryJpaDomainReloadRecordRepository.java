package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内存 JPA domain 刷新记录仓库。
 */
public class InMemoryJpaDomainReloadRecordRepository implements JpaDomainReloadRecordRepository {

  private final int maxRecentRecords;
  private final LinkedHashMap<String, JpaDomainReloadRecord> records = new LinkedHashMap<>();
  private final Map<String, String> idempotencyKeys = new LinkedHashMap<>();

  public InMemoryJpaDomainReloadRecordRepository(int maxRecentRecords) {
    this.maxRecentRecords = maxRecentRecords <= 0 ? 100 : maxRecentRecords;
  }

  @Override
  public synchronized void save(JpaDomainReloadRecord record) {
    if (record == null || !StringUtils.hasText(record.getReloadId())) {
      return;
    }
    records.put(record.getReloadId(), record);
    trim();
  }

  @Override
  public synchronized JpaDomainReloadRecord findById(String reloadId) {
    return StringUtils.hasText(reloadId) ? records.get(reloadId) : null;
  }

  @Override
  public synchronized JpaDomainReloadRecord findByIdempotencyKey(String idempotencyKey) {
    String reloadId = StringUtils.hasText(idempotencyKey) ? idempotencyKeys.get(idempotencyKey) : null;
    return reloadId == null ? null : records.get(reloadId);
  }

  @Override
  public synchronized void bindIdempotencyKey(String idempotencyKey, String reloadId) {
    if (StringUtils.hasText(idempotencyKey) && StringUtils.hasText(reloadId)) {
      idempotencyKeys.put(idempotencyKey, reloadId);
    }
  }

  private void trim() {
    while (records.size() > maxRecentRecords) {
      Iterator<String> iterator = records.keySet().iterator();
      if (!iterator.hasNext()) {
        return;
      }
      String removedReloadId = iterator.next();
      iterator.remove();
      Iterator<Map.Entry<String, String>> keyIterator = idempotencyKeys.entrySet().iterator();
      while (keyIterator.hasNext()) {
        if (removedReloadId.equals(keyIterator.next().getValue())) {
          keyIterator.remove();
        }
      }
    }
  }
}
