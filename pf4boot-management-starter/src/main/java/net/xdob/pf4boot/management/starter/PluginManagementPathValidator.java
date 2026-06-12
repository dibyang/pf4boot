package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginManagementPathValidator {

  public Path resolveStagedPath(String stagingRoot, String stagedPluginPath) {
    if (stagingRoot == null || stagingRoot.trim().isEmpty()) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAVAILABLE,
          "staging root is not configured",
          503);
    }
    if (stagedPluginPath == null || stagedPluginPath.trim().isEmpty()) {
      throw new PluginManagementException(
          PluginManagementErrorCode.INVALID_REQUEST,
          "stagedPluginPath is required",
          400);
    }
    Path root = Paths.get(stagingRoot).toAbsolutePath().normalize();
    Path path = Paths.get(stagedPluginPath);
    if (!path.isAbsolute()) {
      path = root.resolve(path);
    }
    path = path.toAbsolutePath().normalize();
    if (!path.startsWith(root)) {
      throw new PluginManagementException(
          PluginManagementErrorCode.PRECHECK_FAILED,
          "stagedPluginPath must be under staging root",
          422);
    }
    return path;
  }
}
