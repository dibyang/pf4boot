package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.deployment.DeploymentPlan;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentState;
import org.pf4j.PluginState;

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
 * 基于 JSON Lines 的部署记录存储。
 *
 * <p>该实现用于让 HTTP 管理接口的部署记录可跨宿主重启查询。第一阶段只恢复
 * confirm 所需的核心 plan 字段，不承诺完整恢复 rollback snapshot。</p>
 */
public class FilePluginDeploymentRecordStore implements PluginDeploymentRecordStore {

  private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"])*)\"|-?\\d+)");
  private static final String FILE_PREFIX = "deployments-";
  private static final String FILE_SUFFIX = ".jsonl";

  private final Path directory;
  private final Map<String, DeploymentRecord> byId = new HashMap<>();

  public FilePluginDeploymentRecordStore(Path directory) {
    if (directory == null) {
      throw new IllegalArgumentException("deployment store directory must not be null");
    }
    this.directory = directory;
    initialize();
  }

  @Override
  public synchronized DeploymentRecord save(DeploymentRecord record) {
    if (record == null || record.getDeploymentId() == null) {
      return null;
    }
    append(record);
    byId.put(record.getDeploymentId(), record);
    return record;
  }

  @Override
  public synchronized DeploymentRecord findById(String deploymentId) {
    if (deploymentId == null || deploymentId.trim().isEmpty()) {
      return null;
    }
    return byId.get(deploymentId);
  }

  @Override
  public synchronized List<DeploymentRecord> recent(int limit) {
    int max = limit <= 0 ? Integer.MAX_VALUE : limit;
    List<DeploymentRecord> records = new ArrayList<>(byId.values());
    records.sort(Comparator.comparingLong(DeploymentRecord::getUpdatedAt).reversed());
    if (records.size() <= max) {
      return records;
    }
    return new ArrayList<>(records.subList(0, max));
  }

  private void initialize() {
    try {
      Files.createDirectories(directory);
      loadExistingRecords();
    } catch (IOException e) {
      throw new IllegalStateException("PFP-STORE-001 deployment store unavailable: " + directory, e);
    }
  }

  private void loadExistingRecords() throws IOException {
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
        DeploymentRecord record = parse(line);
        if (record != null && record.getDeploymentId() != null) {
          DeploymentRecord current = byId.get(record.getDeploymentId());
          if (current == null || record.getUpdatedAt() >= current.getUpdatedAt()) {
            byId.put(record.getDeploymentId(), record);
          }
        }
      }
    }
  }

  private void append(DeploymentRecord record) {
    Path file = directory.resolve(FILE_PREFIX + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + FILE_SUFFIX);
    try (FileOutputStream outputStream = new FileOutputStream(file.toFile(), true);
         Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      writer.write(toJson(record));
      writer.write('\n');
      writer.flush();
      FileChannel channel = outputStream.getChannel();
      channel.force(true);
    } catch (IOException e) {
      throw new IllegalStateException("PFP-STORE-002 deployment store write failed: " + file, e);
    }
  }

  private String toJson(DeploymentRecord record) {
    DeploymentPlan plan = record.getPlan();
    StringBuilder builder = new StringBuilder(384);
    builder.append('{');
    append(builder, "schemaVersion", 1).append(',');
    append(builder, "deploymentId", record.getDeploymentId()).append(',');
    append(builder, "targetPluginId", record.getTargetPluginId()).append(',');
    append(builder, "state", record.getState() == null ? null : record.getState().name()).append(',');
    append(builder, "createdAt", record.getCreatedAt()).append(',');
    append(builder, "updatedAt", record.getUpdatedAt()).append(',');
    append(builder, "message", record.getMessage()).append(',');
    append(builder, "durationMillis", record.getDurationMillis()).append(',');
    append(builder, "errorCode", record.getErrorCode()).append(',');
    append(builder, "planDeploymentId", plan == null ? null : plan.getDeploymentId()).append(',');
    append(builder, "planTargetPluginId", plan == null ? null : plan.getTargetPluginId()).append(',');
    append(builder, "planStagedPluginPath", plan == null ? null : plan.getStagedPluginPath()).append(',');
    append(builder, "planCurrentPluginPath", plan == null ? null : plan.getCurrentPluginPath()).append(',');
    append(builder, "planCurrentVersion", plan == null ? null : plan.getCurrentVersion()).append(',');
    append(builder, "planCurrentState", plan == null || plan.getCurrentState() == null ? null : plan.getCurrentState().name()).append(',');
    append(builder, "planStagedVersion", plan == null ? null : plan.getStagedVersion()).append(',');
    append(builder, "planStagedRequires", plan == null ? null : plan.getStagedRequires());
    builder.append('}');
    return builder.toString();
  }

  private DeploymentRecord parse(String line) {
    if (line == null || !line.trim().startsWith("{") || !line.trim().endsWith("}")) {
      return null;
    }
    Map<String, String> fields = fields(line);
    String deploymentId = fields.get("deploymentId");
    DeploymentState state = deploymentState(fields.get("state"));
    DeploymentPlan plan = plan(fields);
    return new DeploymentRecord(
        deploymentId,
        fields.get("targetPluginId"),
        state,
        longValue(fields.get("createdAt")),
        longValue(fields.get("updatedAt")),
        fields.get("message"),
        plan,
        state == null ? Collections.<DeploymentState>emptyList() : Collections.singletonList(state),
        longValue(fields.get("durationMillis")),
        fields.get("errorCode"));
  }

  private DeploymentPlan plan(Map<String, String> fields) {
    if (!hasText(fields.get("planDeploymentId"))
        && !hasText(fields.get("planTargetPluginId"))
        && !hasText(fields.get("planStagedPluginPath"))) {
      return null;
    }
    return new DeploymentPlan(
        fields.get("planDeploymentId"),
        fields.get("planTargetPluginId"),
        fields.get("planStagedPluginPath"),
        fields.get("planCurrentPluginPath"),
        fields.get("planCurrentVersion"),
        pluginState(fields.get("planCurrentState")),
        fields.get("planStagedVersion"),
        fields.get("planStagedRequires"),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.emptyList(),
        null);
  }

  private Map<String, String> fields(String line) {
    Map<String, String> fields = new HashMap<>();
    Matcher matcher = FIELD.matcher(line);
    while (matcher.find()) {
      String value = matcher.group(3) == null ? matcher.group(2) : matcher.group(3);
      fields.put(matcher.group(1), unescape(stripQuotes(value)));
    }
    return fields;
  }

  private DeploymentState deploymentState(String value) {
    if (!hasText(value)) {
      return null;
    }
    try {
      return DeploymentState.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private PluginState pluginState(String value) {
    if (!hasText(value)) {
      return null;
    }
    try {
      return PluginState.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private long longValue(String value) {
    if (!hasText(value)) {
      return 0;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0;
    }
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

  private String stripQuotes(String value) {
    if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
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
