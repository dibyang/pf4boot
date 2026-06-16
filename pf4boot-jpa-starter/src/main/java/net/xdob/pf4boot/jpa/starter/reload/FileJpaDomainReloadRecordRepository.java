package net.xdob.pf4boot.jpa.starter.reload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于本地文件的 JPA domain reload 记录仓库。
 *
 * <p>记录按 JSON Lines 追加写入，启动时重建内存索引；幂等键和 latest 指针写入独立索引文件。
 * 该实现面向单宿主进程，不提供多进程并发写一致性。</p>
 */
public class FileJpaDomainReloadRecordRepository implements JpaDomainReloadRecordRepository {

  private static final String RECORD_FILE_PREFIX = "jpa-reloads-";
  private static final String RECORD_FILE_SUFFIX = ".jsonl";
  private static final String LATEST_FILE = "latest.json";
  private static final String IDEMPOTENCY_DIR = "idempotency";

  private final Path directory;
  private final Path idempotencyDirectory;
  private final int maxRecentRecords;
  private final boolean failClosed;
  private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
  private final LinkedHashMap<String, JpaDomainReloadRecord> records = new LinkedHashMap<>();
  private final Map<String, String> idempotencyKeys = new LinkedHashMap<>();
  private String latestReloadId;

  public FileJpaDomainReloadRecordRepository(Path directory, int maxRecentRecords, boolean failClosed) {
    if (directory == null) {
      throw new IllegalArgumentException("directory is required");
    }
    this.directory = directory;
    this.idempotencyDirectory = directory.resolve(IDEMPOTENCY_DIR);
    this.maxRecentRecords = maxRecentRecords <= 0 ? 100 : maxRecentRecords;
    this.failClosed = failClosed;
    initialize();
  }

  @Override
  public synchronized void save(JpaDomainReloadRecord record) {
    if (record == null || !StringUtils.hasText(record.getReloadId())) {
      return;
    }
    ensureDirectories();
    appendRecord(record);
    records.put(record.getReloadId(), record);
    latestReloadId = record.getReloadId();
    writeJsonAtomically(directory.resolve(LATEST_FILE), new LatestIndex(latestReloadId));
    if (record.getRequest() != null && StringUtils.hasText(record.getRequest().getIdempotencyKey())) {
      bindIdempotencyKey(record.getRequest().getIdempotencyKey(), record.getReloadId());
    }
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
    if (!StringUtils.hasText(idempotencyKey) || !StringUtils.hasText(reloadId)) {
      return;
    }
    ensureDirectories();
    idempotencyKeys.put(idempotencyKey, reloadId);
    writeJsonAtomically(
        idempotencyDirectory.resolve(hash(idempotencyKey) + ".json"),
        new IdempotencyIndex(idempotencyKey, reloadId));
  }

  @Override
  public synchronized JpaDomainReloadRecord findLatest() {
    JpaDomainReloadRecord latest = findById(latestReloadId);
    if (latest != null) {
      return latest;
    }
    List<JpaDomainReloadRecord> recent = recent(1);
    return recent.isEmpty() ? null : recent.get(0);
  }

  @Override
  public synchronized List<JpaDomainReloadRecord> recent(int limit) {
    if (limit <= 0 || records.isEmpty()) {
      return Collections.emptyList();
    }
    int max = Math.min(limit, maxRecentRecords);
    List<JpaDomainReloadRecord> result = new ArrayList<>(records.values());
    Collections.sort(result, reloadRecordComparator(false));
    if (result.size() > max) {
      result = new ArrayList<>(result.subList(0, max));
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

  private void initialize() {
    ensureDirectories();
    loadRecords();
    rebuildIdempotencyKeysFromRecords();
    loadIdempotencyIndexes();
    loadLatestIndex();
  }

  private void ensureDirectories() {
    try {
      Files.createDirectories(directory);
      Files.createDirectories(idempotencyDirectory);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create JPA reload record directory: " + directory, e);
    }
  }

  private void loadRecords() {
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, RECORD_FILE_PREFIX + "*" + RECORD_FILE_SUFFIX)) {
      for (Path path : stream) {
        files.add(path);
      }
    } catch (IOException e) {
      handleLoadFailure("Failed to list JPA reload record files: " + directory, e);
      return;
    }
    Collections.sort(files);
    for (Path file : files) {
      loadRecordFile(file);
    }
  }

  private void loadRecordFile(Path file) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      handleLoadFailure("Failed to read JPA reload record file: " + file, e);
      return;
    }
    for (String line : lines) {
      if (!StringUtils.hasText(line)) {
        continue;
      }
      try {
        JpaDomainReloadRecord record = gson.fromJson(line, JpaDomainReloadRecord.class);
        if (record != null && StringUtils.hasText(record.getReloadId())) {
          records.put(record.getReloadId(), record);
        }
      } catch (RuntimeException e) {
        handleLoadFailure("Failed to parse JPA reload record file: " + file, e);
      }
    }
  }

  private void rebuildIdempotencyKeysFromRecords() {
    for (JpaDomainReloadRecord record : records.values()) {
      if (record.getRequest() != null && StringUtils.hasText(record.getRequest().getIdempotencyKey())) {
        idempotencyKeys.put(record.getRequest().getIdempotencyKey(), record.getReloadId());
      }
    }
  }

  private void loadIdempotencyIndexes() {
    if (!Files.isDirectory(idempotencyDirectory)) {
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(idempotencyDirectory, "*.json")) {
      for (Path file : stream) {
        loadIdempotencyIndex(file);
      }
    } catch (IOException e) {
      handleLoadFailure("Failed to list JPA reload idempotency indexes: " + idempotencyDirectory, e);
    }
  }

  private void loadIdempotencyIndex(Path file) {
    try {
      String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      IdempotencyIndex index = gson.fromJson(json, IdempotencyIndex.class);
      if (index != null
          && StringUtils.hasText(index.idempotencyKey)
          && StringUtils.hasText(index.reloadId)
          && records.containsKey(index.reloadId)) {
        idempotencyKeys.put(index.idempotencyKey, index.reloadId);
      }
    } catch (RuntimeException | IOException e) {
      handleLoadFailure("Failed to load JPA reload idempotency index: " + file, e);
    }
  }

  private void loadLatestIndex() {
    Path latestFile = directory.resolve(LATEST_FILE);
    if (!Files.isRegularFile(latestFile)) {
      return;
    }
    try {
      String json = new String(Files.readAllBytes(latestFile), StandardCharsets.UTF_8);
      LatestIndex index = gson.fromJson(json, LatestIndex.class);
      if (index != null && records.containsKey(index.reloadId)) {
        latestReloadId = index.reloadId;
      }
    } catch (RuntimeException | IOException e) {
      handleLoadFailure("Failed to load JPA reload latest index: " + latestFile, e);
    }
  }

  private void appendRecord(JpaDomainReloadRecord record) {
    Path file = directory.resolve(recordFileName(System.currentTimeMillis()));
    String line = gson.toJson(record) + System.lineSeparator();
    try {
      Files.write(file, line.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to append JPA reload record: " + file, e);
    }
  }

  private void writeJsonAtomically(Path target, Object value) {
    Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
    try {
      Files.createDirectories(target.getParent());
      Files.write(temp, gson.toJson(value).getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      try {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write JPA reload index: " + target, e);
    }
  }

  private void handleLoadFailure(String message, Exception e) {
    if (failClosed) {
      throw new IllegalStateException(message, e);
    }
  }

  private static String recordFileName(long timestamp) {
    return RECORD_FILE_PREFIX + new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp)) + RECORD_FILE_SUFFIX;
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

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        builder.append(String.format("%02x", b & 0xff));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static class LatestIndex {
    private String reloadId;

    LatestIndex(String reloadId) {
      this.reloadId = reloadId;
    }
  }

  private static class IdempotencyIndex {
    private String idempotencyKey;
    private String reloadId;

    IdempotencyIndex(String idempotencyKey, String reloadId) {
      this.idempotencyKey = idempotencyKey;
      this.reloadId = reloadId;
    }
  }
}
