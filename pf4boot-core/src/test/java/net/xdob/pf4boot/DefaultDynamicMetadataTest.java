package net.xdob.pf4boot;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultDynamicMetadataTest {

  @Test
  public void recordsEntityCandidatesAsSnapshot() {
    DefaultDynamicMetadata metadata = new DefaultDynamicMetadata();

    metadata.addEntityClasses(EntityOne.class, EntityTwo.class);
    metadata.removeEntityClasses(EntityTwo.class);

    Set<Class<?>> entityClasses = metadata.getAllEntityClasses();
    assertTrue(entityClasses.contains(EntityOne.class));
    assertFalse(entityClasses.contains(EntityTwo.class));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void syncFailsClearlyBecauseRuntimeJpaMetadataSyncIsUnsupported() {
    new DefaultDynamicMetadata().sync();
  }

  private static class EntityOne {
  }

  private static class EntityTwo {
  }
}
