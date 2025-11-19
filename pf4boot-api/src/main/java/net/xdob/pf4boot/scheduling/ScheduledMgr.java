package net.xdob.pf4boot.scheduling;

import net.xdob.pf4boot.Pf4bootPlugin;

public interface ScheduledMgr {
	void unregisterScheduledTasks(Pf4bootPlugin pf4bootPlugin);

	void registerScheduledTasks(Pf4bootPlugin pf4bootPlugin);

	void destroy();
}
