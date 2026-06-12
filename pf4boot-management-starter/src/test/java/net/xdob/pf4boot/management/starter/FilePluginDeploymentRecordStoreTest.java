package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.deployment.DeploymentPlan;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentState;
import org.junit.Test;
import org.pf4j.PluginState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FilePluginDeploymentRecordStoreTest {

  @Test
  public void appendAndReadDeploymentRecordAfterRestart() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-deployment-store");
    FilePluginDeploymentRecordStore store = new FilePluginDeploymentRecordStore(directory);
    DeploymentRecord record = record("dep-1", DeploymentState.PRECHECKED);

    store.save(record);
    FilePluginDeploymentRecordStore reloaded = new FilePluginDeploymentRecordStore(directory);

    DeploymentRecord loaded = reloaded.findById("dep-1");
    assertNotNull(loaded);
    assertEquals(DeploymentState.PRECHECKED, loaded.getState());
    assertEquals("sample", loaded.getTargetPluginId());
    assertNotNull(loaded.getPlan());
    assertEquals("sample", loaded.getPlan().getTargetPluginId());
    assertEquals("sample.zip", loaded.getPlan().getStagedPluginPath());
  }

  @Test
  public void recentReturnsNewestRecordFirst() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-deployment-store");
    FilePluginDeploymentRecordStore store = new FilePluginDeploymentRecordStore(directory);
    store.save(record("dep-1", DeploymentState.PRECHECKED));
    store.save(new DeploymentRecord(
        "dep-2",
        "sample",
        DeploymentState.SUCCEEDED,
        1,
        100,
        "ok",
        plan("dep-2")));

    assertEquals("dep-2", store.recent(1).get(0).getDeploymentId());
  }

  private DeploymentRecord record(String deploymentId, DeploymentState state) {
    return new DeploymentRecord(
        deploymentId,
        "sample",
        state,
        1,
        2,
        "prechecked",
        plan(deploymentId));
  }

  private DeploymentPlan plan(String deploymentId) {
    return new DeploymentPlan(
        deploymentId,
        "sample",
        "sample.zip",
        "old-sample.zip",
        "0.9.0",
        PluginState.STARTED,
        "1.0.0",
        "0.0.0",
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.emptyList(),
        null);
  }
}
