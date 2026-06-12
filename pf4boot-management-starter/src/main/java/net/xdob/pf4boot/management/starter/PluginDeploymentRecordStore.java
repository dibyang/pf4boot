package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.deployment.DeploymentRecord;

import java.util.List;

/**
 * Deployment records used by HTTP management APIs.
 */
public interface PluginDeploymentRecordStore {

  DeploymentRecord save(DeploymentRecord record);

  DeploymentRecord findById(String deploymentId);

  List<DeploymentRecord> recent(int limit);
}
