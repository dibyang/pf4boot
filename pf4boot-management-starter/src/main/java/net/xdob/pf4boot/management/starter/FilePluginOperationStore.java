package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginOperationRecord;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 JSON Lines 的管理操作记录存储。
 *
 * <p>该实现用于管理接口的幂等与重启恢复场景。每次保存都会追加一行完整 JSON，
 * 启动时扫描目录重建内存索引。写入方法使用同步块保证单 JVM 内的幂等 key 预留原子性；
 * 多 JVM 同时写同一目录不在当前阶段支持范围内。</p>
 */
public class FilePluginOperationStore implements PluginOperationStore {

  private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"])*)\"|true|false|-?\\d+)");
  private static final String FILE_PREFIX = "operations-";
  private static final String FILE_SUFFIX = ".jsonl";

  private final Path directory;
  private final Map<String, PluginOperationRecord> byOperationId = new HashMap<>();
  private final Map<String, String> byIdempotencyKey = new HashMap<>();
  private final Map<String, String> byDeploymentId = new HashMap<>();

  public FilePluginOperationStore(Path directory) {
    if (directory == null) {
      throw new IllegalArgumentException("operation store directory must not be null");
    }
    this.directory = directory;
    initialize();
  }

  @Override
  public synchronized PluginOperationRecord save(PluginOperationRecord record) {
    if (record == null || !StringUtils.hasText(record.getOperationId())) {
      return null;
    }
    touch(record);
    append(record);
    index(record);
    return record;
  }

  @Override
  public synchronized PluginOperationRecord saveIfIdempotencyKeyAbsent(PluginOperationRecord record) {
    if (record == null || !StringUtils.hasText(record.getOperationId())
        || !StringUtils.hasText(record.getIdempotencyKey())) {
      save(record);
      return null;
    }
    PluginOperationRecord existing = findByIdempotencyKey(record.getIdempotencyKey());
    if (existing != null) {
      return existing;
    }
    touch(record);
    append(record);
    index(record);
    return null;
  }

  @Override
  public synchronized PluginOperationRecord findById(String operationId) {
    return byOperationId.get(operationId);
  }

  @Override
  public synchronized PluginOperationRecord findByIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      return null;
    }
    String operationId = byIdempotencyKey.get(idempotencyKey);
    return operationId == null ? null : byOperationId.get(operationId);
  }

  @Override
  public synchronized PluginOperationRecord findByDeploymentId(String deploymentId) {
    if (!StringUtils.hasText(deploymentId)) {
      return null;
    }
    String operationId = byDeploymentId.get(deploymentId);
    return operationId == null ? null : byOperationId.get(operationId);
  }

  @Override
  public synchronized List<PluginOperationRecord> recent(int limit) {
    int size = limit <= 0 ? Integer.MAX_VALUE : limit;
    List<PluginOperationRecord> records = new ArrayList<>(byOperationId.values());
    records.sort(Comparator.comparingLong(PluginOperationRecord::getUpdatedAt).reversed());
    if (records.size() <= size) {
      return records;
    }
    return new ArrayList<>(records.subList(0, size));
  }

  @Override
  public synchronized List<PluginOperationRecord> scanRecoverableRecords() {
    List<PluginOperationRecord> records = new ArrayList<>();
    for (PluginOperationRecord record : byOperationId.values()) {
      if (isRecoverableState(record.getState())) {
        records.add(record);
      }
    }
    records.sort(Comparator.comparingLong(PluginOperationRecord::getUpdatedAt));
    return records;
  }

  private void initialize() {
    try {
      Files.createDirectories(directory);
      loadExistingRecords();
    } catch (IOException e) {
      throw new IllegalStateException("PFP-STORE-001 operation store unavailable: " + directory, e);
    }
  }

  private void loadExistingRecords() throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, FILE_PREFIX + "*" + FILE_SUFFIX)) {
      for (Path file : stream) {
        files.add(file);
      }
    }
    Collections.sort(files);
    for (Path file : files) {
      loadFile(file);
    }
  }

  private void loadFile(Path file) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        PluginOperationRecord record = parse(line);
        if (record != null && StringUtils.hasText(record.getOperationId())) {
          PluginOperationRecord current = byOperationId.get(record.getOperationId());
          if (current == null || record.getUpdatedAt() >= current.getUpdatedAt()) {
            index(record);
          }
        }
      }
    }
  }

  private void append(PluginOperationRecord record) {
    Path file = directory.resolve(FILE_PREFIX + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + FILE_SUFFIX);
    try (FileOutputStream outputStream = new FileOutputStream(file.toFile(), true);
         Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      writer.write(toJson(record));
      writer.write('\n');
      writer.flush();
      FileChannel channel = outputStream.getChannel();
      channel.force(true);
    } catch (IOException e) {
      throw new IllegalStateException("PFP-STORE-002 operation store write failed: " + file, e);
    }
  }

  private void touch(PluginOperationRecord record) {
    long now = System.currentTimeMillis();
    if (record.getCreatedAt() == 0) {
      record.setCreatedAt(now);
    }
    record.setUpdatedAt(now);
  }

  private void index(PluginOperationRecord record) {
    byOperationId.put(record.getOperationId(), record);
    if (StringUtils.hasText(record.getIdempotencyKey())) {
      byIdempotencyKey.put(record.getIdempotencyKey(), record.getOperationId());
    }
    if (StringUtils.hasText(record.getDeploymentId())) {
      byDeploymentId.put(record.getDeploymentId(), record.getOperationId());
    }
  }

  private boolean isRecoverableState(String state) {
    return "STARTED".equals(state)
        || "EXECUTING".equals(state)
        || "ROLLING_BACK".equals(state);
  }

  private String toJson(PluginOperationRecord record) {
    StringBuilder builder = new StringBuilder(256);
    builder.append('{');
    append(builder, "schemaVersion", 1).append(',');
    append(builder, "operationId", record.getOperationId()).append(',');
    append(builder, "requestId", record.getRequestId()).append(',');
    append(builder, "operation", record.getOperation() == null ? null : record.getOperation().name()).append(',');
    append(builder, "principalId", record.getPrincipalId()).append(',');
    append(builder, "pluginId", record.getPluginId()).append(',');
    append(builder, "deploymentId", record.getDeploymentId()).append(',');
    append(builder, "idempotencyKey", record.getIdempotencyKey()).append(',');
    append(builder, "requestHash", record.getRequestHash()).append(',');
    append(builder, "responseCode", record.getResponseCode()).append(',');
    append(builder, "responseMessage", record.getResponseMessage()).append(',');
    append(builder, "success", record.isSuccess()).append(',');
    append(builder, "state", record.getState()).append(',');
    append(builder, "responseBodySummary", record.getResponseBodySummary()).append(',');
    append(builder, "createdAt", record.getCreatedAt()).append(',');
    append(builder, "updatedAt", record.getUpdatedAt());
    builder.append('}');
    return builder.toString();
  }

  private StringBuilder append(StringBuilder builder, String name, String value) {
    builder.append('"').append(name).append('"').append(':');
    if (value == null) {
      builder.append("\"\"");
    } else {
      builder.append('"').append(escape(value)).append('"');
    }
    return builder;
  }

  private StringBuilder append(StringBuilder builder, String name, long value) {
    return builder.append('"').append(name).append('"').append(':').append(value);
  }

  private StringBuilder append(StringBuilder builder, String name, boolean value) {
    return builder.append('"').append(name).append('"').append(':').append(value);
  }

  private PluginOperationRecord parse(String line) {
    if (line == null || !line.trim().startsWith("{") || !line.trim().endsWith("}")) {
      return null;
    }
    Map<String, String> fields = new HashMap<>();
    Matcher matcher = STRING_FIELD.matcher(line);
    while (matcher.find()) {
      String raw = matcher.group(3) == null ? matcher.group(2) : matcher.group(3);
      fields.put(matcher.group(1), unescape(stripQuotes(raw)));
    }
    PluginOperationRecord record = new PluginOperationRecord();
    record.setOperationId(fields.get("operationId"));
    record.setRequestId(fields.get("requestId"));
    record.setOperation(operation(fields.get("operation")));
    record.setPrincipalId(fields.get("principalId"));
    record.setPluginId(fields.get("pluginId"));
    record.setDeploymentId(fields.get("deploymentId"));
    record.setIdempotencyKey(fields.get("idempotencyKey"));
    record.setRequestHash(fields.get("requestHash"));
    record.setResponseCode(fields.get("responseCode"));
    record.setResponseMessage(fields.get("responseMessage"));
    record.setSuccess(Boolean.parseBoolean(fields.get("success")));
    record.setState(fields.get("state"));
    record.setResponseBodySummary(fields.get("responseBodySummary"));
    record.setCreatedAt(longValue(fields.get("createdAt")));
    record.setUpdatedAt(longValue(fields.get("updatedAt")));
    return record;
  }

  private PluginManagementOperation operation(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return PluginManagementOperation.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private long longValue(String value) {
    if (!StringUtils.hasText(value)) {
      return 0;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String stripQuotes(String value) {
    if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }

  private String unescape(String value) {
    if (value == null) {
      return null;
    }
    return value.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t");
  }
}
