package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Pf4bootPluginsEndpointTest {

  @Test
  public void endpointReturnsInspectorSnapshotsWithoutMutationOperations() {
    PluginRuntimeSnapshot snapshot = new PluginRuntimeSnapshot();
    snapshot.setPluginId("sample");
    snapshot.setLastStartDurationMillis(12);
    Pf4bootPluginsEndpoint endpoint = new Pf4bootPluginsEndpoint(
        () -> Collections.singletonList(snapshot));

    List<PluginRuntimeSnapshot> snapshots = endpoint.plugins();

    assertEquals(1, snapshots.size());
    assertEquals("sample", snapshots.get(0).getPluginId());
    assertEquals(12, snapshots.get(0).getLastStartDurationMillis());
  }
}
