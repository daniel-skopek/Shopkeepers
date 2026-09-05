package com.nisovin.shopkeepers.util.bukkit;

/**
 * A scheduled task, regardless of whether it has been scheduled via the Bukkit scheduler or the
 * Folia scheduler API.
 */
public interface ScheduledTask {

	/**
	 * Cancels this task.
	 */
	public void cancel();

	/**
	 * Checks whether this task has been cancelled.
	 * 
	 * @return <code>true</code> if this task has been cancelled
	 */
	public boolean isCancelled();
}
