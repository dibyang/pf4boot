package net.xdob.pf4boot.scheduling;

import net.xdob.pf4boot.Pf4bootPlugin;

import java.util.Collection;

public interface ScheduledMgr {
	void unregisterScheduledTasks(Pf4bootPlugin pf4bootPlugin);

	void registerScheduledTasks(Pf4bootPlugin pf4bootPlugin);

  default void beginDrain(Collection<String> pluginIds) {
  }

  default boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) throws InterruptedException {
    return true;
  }

  default void endDrain(Collection<String> pluginIds) {
  }

	void destroy();
}
