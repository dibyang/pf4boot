package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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

  @Override
  public synchronized JpaDomainReloadRecord findLatest() {
    List<JpaDomainReloadRecord> recent = recent(1);
    return recent.isEmpty() ? null : recent.get(0);
  }

  @Override
  public synchronized List<JpaDomainReloadRecord> recent(int limit) {
    if (limit <= 0 || records.isEmpty()) {
      return Collections.emptyList();
    }
    List<JpaDomainReloadRecord> result = new ArrayList<>(records.values());
    Collections.sort(result, reloadRecordComparator(false));
    if (result.size() > limit) {
      result = new ArrayList<>(result.subList(0, limit));
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public synchronized List<JpaDomainReloadRecord> scanRecoverableRecords() {
    List<JpaDomainReloadRecord> result = new ArrayList<>();
    for (JpaDomainReloadRecord record : records.values()) {
      if (isRecoverable(record)) {
        result.add(record);
      }
    }
    Collections.sort(result, reloadRecordComparator(true));
    return Collections.unmodifiableList(result);
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

  private static boolean isRecoverable(JpaDomainReloadRecord record) {
    return record != null
        && JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED == record.getState();
  }

  private static Comparator<JpaDomainReloadRecord> reloadRecordComparator(boolean oldestFirst) {
    return (left, right) -> {
      long leftTime = timestamp(left);
      long rightTime = timestamp(right);
      int timeCompare = Long.compare(leftTime, rightTime);
      if (!oldestFirst) {
        timeCompare = -timeCompare;
      }
      if (timeCompare != 0) {
        return timeCompare;
      }
      String leftId = left == null ? null : left.getReloadId();
      String rightId = right == null ? null : right.getReloadId();
      if (leftId == null && rightId == null) {
        return 0;
      }
      if (leftId == null) {
        return 1;
      }
      if (rightId == null) {
        return -1;
      }
      return leftId.compareTo(rightId);
    };
  }

  private static long timestamp(JpaDomainReloadRecord record) {
    if (record == null) {
      return 0L;
    }
    return record.getFinishedAt() > 0L ? record.getFinishedAt() : record.getStartedAt();
  }
}
