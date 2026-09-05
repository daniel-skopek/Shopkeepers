package com.nisovin.shopkeepers.util.bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Scheduler related utilities.
 * <p>
 * On servers that provide the Folia scheduler API, tasks are scheduled via the Folia scheduler
 * API. On all other servers, tasks are scheduled via the {@link BukkitScheduler}.
 */
public final class SchedulerUtils {

	/**
	 * Creates an {@link Executor} that executes tasks on the server's main thread using
	 * {@link #runOnMainThreadOrOmit(Plugin, Runnable)}.
	 * <p>
	 * If the thread registering the task is already the server's main thread, the task is run
	 * immediately. Otherwise, it is scheduled using the {@link BukkitScheduler}. If the plugin is
	 * not enabled at the time of task registration, the task is omitted.
	 * 
	 * @param plugin
	 *            the plugin
	 * @return the executor
	 */
	public static Executor createSyncExecutor(Plugin plugin) {
		return (runnable) -> runOnMainThreadOrOmit(plugin, runnable);
	}

	/**
	 * Creates an {@link Executor} that executes tasks using
	 * {@link #runAsyncTaskOrOmit(Plugin, Runnable)}.
	 * 
	 * @param plugin
	 *            the plugin
	 * @return the executor
	 */
	public static Executor createAsyncExecutor(Plugin plugin) {
		return (runnable) -> runAsyncTaskOrOmit(plugin, runnable);
	}

	public static int getActiveAsyncTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		if (FoliaScheduler.isAvailable()) {
			// The Folia scheduler API does not provide access to the currently active async tasks.
			return 0;
		}
		int workers = 0;
		for (BukkitWorker worker : Bukkit.getScheduler().getActiveWorkers()) {
			if (worker.getOwner().equals(plugin)) {
				workers++;
			}
		}
		return workers;
	}

	private static void validatePluginTask(Plugin plugin, Runnable task) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
	}

	/**
	 * Checks whether the server provides the Folia scheduler API.
	 * 
	 * @return <code>true</code> if the Folia scheduler API is available
	 */
	public static boolean isFolia() {
		return FoliaScheduler.isAvailable();
	}

	/**
	 * Teleports the given entity to the given location.
	 * <p>
	 * On servers that provide the Folia scheduler API, synchronous entity teleports are not
	 * allowed, so this schedules an asynchronous teleport instead. The returned future completes
	 * with the result of the teleport.
	 * <p>
	 * On all other servers, this performs a synchronous teleport and returns an already completed
	 * future.
	 * 
	 * @param entity
	 *            the entity, not <code>null</code>
	 * @param location
	 *            the destination location, not <code>null</code>
	 * @return the teleport result, or <code>null</code> if the teleport could not be scheduled
	 */
	public static @Nullable CompletableFuture<Boolean> teleportAsync(
			Entity entity,
			Location location
	) {
		Validate.notNull(entity, "entity is null");
		Validate.notNull(location, "location is null");
		if (FoliaScheduler.isAvailable()) {
			return FoliaScheduler.teleportAsync(entity, location);
		}
		// Synchronous teleport on non-Folia servers:
		return CompletableFuture.completedFuture(Boolean.valueOf(entity.teleport(location)));
	}

	/**
	 * Checks if the current thread is the server's main thread.
	 * <p>
	 * On servers that provide the Folia scheduler API, this checks if the current thread is the
	 * global region ticking thread instead.
	 * <p>
	 * Note: During the server's startup and shutdown phases (e.g. while plugins are enabled and
	 * disabled), the server is not yet (or no longer) ticking the global region, so
	 * {@link #isGlobalTickThread()} returns <code>false</code> even though plugins run on the
	 * primary thread. We therefore also check {@link Bukkit#isPrimaryThread()}.
	 * 
	 * @return <code>true</code> if currently running on the main thread
	 */
	public static boolean isMainThread() {
		if (Bukkit.isPrimaryThread()) {
			return true;
		}
		return isGlobalTickThread();
	}

	/**
	 * Checks if the current thread is the server's global region ticking thread.
	 * <p>
	 * On servers that do not provide the Folia scheduler API, this is equivalent to
	 * {@link #isMainThread()}.
	 * 
	 * @return <code>true</code> if currently running on the global region ticking thread
	 */
	public static boolean isGlobalTickThread() {
		if (FoliaScheduler.isAvailable()) {
			return FoliaScheduler.isGlobalTickThread();
		} else {
			return Bukkit.isPrimaryThread();
		}
	}

	/**
	 * Cancels all tasks of the specified plugin.
	 * <p>
	 * On servers that provide the Folia scheduler API, tasks are already automatically cancelled
	 * when the plugin is disabled, so this does nothing there.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 */
	public static void cancelTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		if (!FoliaScheduler.isAvailable()) {
			Bukkit.getScheduler().cancelTasks(plugin);
		}
	}

	/**
	 * Schedules the given task to be run on the primary thread if required.
	 * <p>
	 * If the current thread is already the primary thread, the task will be run immediately.
	 * Otherwise, it attempts to schedule the task to run on the server's primary thread. However,
	 * if the plugin is disabled, the task won't be scheduled.
	 * 
	 * @param plugin
	 *            the plugin to use for scheduling, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return <code>true</code> if the task was run or successfully scheduled to be run,
	 *         <code>false</code> otherwise
	 */
	public static boolean runOnMainThreadOrOmit(Plugin plugin, Runnable task) {
		validatePluginTask(plugin, task);
		if (isMainThread()) {
			task.run();
			return true;
		} else {
			return (runTaskOrOmit(plugin, task) != null);
		}
	}

	public static @Nullable ScheduledTask runTaskOrOmit(Plugin plugin, Runnable task) {
		return runTaskLaterOrOmit(plugin, task, 0L);
	}

	public static @Nullable ScheduledTask runTaskLaterOrOmit(
			Plugin plugin,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				if (delay <= 0) {
					// Runs on the next tick:
					return FoliaScheduler.run(plugin, task);
				} else {
					return FoliaScheduler.runDelayed(plugin, task, delay);
				}
			} else {
				try {
					BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
					return new BukkitTaskAdapter(bukkitTask);
				} catch (IllegalPluginAccessException e) {
					// Couldn't register task: The plugin got disabled just now.
				}
			}
		}
		return null;
	}

	public static @Nullable ScheduledTask runTaskTimerOrOmit(
			Plugin plugin,
			Runnable task,
			long delay,
			long period
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				// Folia requires the initial delay and period to be at least 1 tick:
				return FoliaScheduler.runAtFixedRate(
						plugin,
						task,
						Math.max(1L, delay),
						Math.max(1L, period)
				);
			} else {
				try {
					BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(
							plugin, task, delay, period);
					return new BukkitTaskAdapter(bukkitTask);
				} catch (IllegalPluginAccessException e) {
					// Couldn't register task: The plugin got disabled just now.
				}
			}
		}
		return null;
	}

	// ENTITY

	public static @Nullable ScheduledTask runTaskOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task
	) {
		return runTaskLaterOrOmit(plugin, entity, task, 0L);
	}

	public static @Nullable ScheduledTask runTaskLaterOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delay
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(entity, "entity is null");
		Validate.notNull(task, "task is null");
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				if (delay <= 0) {
					return FoliaScheduler.run(plugin, entity, task);
				} else {
					return FoliaScheduler.runDelayed(plugin, entity, task, delay);
				}
			} else {
				// The entity scheduler is only available on Folia. Fall back to the global
				// scheduler:
				return runTaskLaterOrOmit(plugin, task, delay);
			}
		}
		return null;
	}

	public static @Nullable ScheduledTask runTaskTimerOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delay,
			long period
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(entity, "entity is null");
		Validate.notNull(task, "task is null");
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				return FoliaScheduler.runAtFixedRate(
						plugin,
						entity,
						task,
						Math.max(1L, delay),
						Math.max(1L, period)
				);
			} else {
				return runTaskTimerOrOmit(plugin, task, delay, period);
			}
		}
		return null;
	}

	// REGION

	public static @Nullable ScheduledTask runTaskOrOmit(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task
	) {
		return runTaskLaterOrOmit(plugin, world, chunkX, chunkZ, task, 0L);
	}

	public static @Nullable ScheduledTask runTaskLaterOrOmit(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task,
			long delay
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(world, "world is null");
		Validate.notNull(task, "task is null");
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				if (delay <= 0) {
					return FoliaScheduler.run(plugin, world, chunkX, chunkZ, task);
				} else {
					return FoliaScheduler.runDelayed(plugin, world, chunkX, chunkZ, task, delay);
				}
			} else {
				return runTaskLaterOrOmit(plugin, task, delay);
			}
		}
		return null;
	}

	public static @Nullable ScheduledTask runTaskTimerOrOmit(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task,
			long delay,
			long period
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(world, "world is null");
		Validate.notNull(task, "task is null");
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				return FoliaScheduler.runAtFixedRate(
						plugin,
						world,
						chunkX,
						chunkZ,
						task,
						Math.max(1L, delay),
						Math.max(1L, period)
				);
			} else {
				return runTaskTimerOrOmit(plugin, task, delay, period);
			}
		}
		return null;
	}

	// ASYNC

	public static @Nullable ScheduledTask runAsyncTaskOrOmit(Plugin plugin, Runnable task) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			return runAsyncTaskOrOmit(plugin, (Consumer<ScheduledTask>) (scheduledTask) -> task.run());
		}
		return null;
	}

	/**
	 * Schedules the given task to be run asynchronously.
	 * <p>
	 * The task receives its own {@link ScheduledTask} handle, which it can for example use to check
	 * whether it got cancelled.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return the scheduled task, or <code>null</code> if the task could not be scheduled
	 */
	public static @Nullable ScheduledTask runAsyncTaskOrOmit(
			Plugin plugin,
			Consumer<? super ScheduledTask> task
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			if (FoliaScheduler.isAvailable()) {
				return FoliaScheduler.runAsyncNow(plugin, task);
			} else {
				try {
					final ScheduledTask[] taskHolder = new ScheduledTask[1];
					BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
						// The holder has already been set by the time this runs:
						task.accept(Unsafe.castNonNull(taskHolder[0]));
					});
					ScheduledTask scheduledTask = new BukkitTaskAdapter(bukkitTask);
					taskHolder[0] = scheduledTask;
					return scheduledTask;
				} catch (IllegalPluginAccessException e) {
					// Couldn't register task: The plugin got disabled just now.
				}
			}
		}
		return null;
	}

	/**
	 * Awaits the completion of async tasks of the specified plugin.
	 * <p>
	 * If a logger is specified, it will be used to print informational messages suited to the
	 * context of this method being called during disabling of the plugin.
	 * 
	 * @param plugin
	 *            the plugin
	 * @param asyncTasksTimeoutSeconds
	 *            the duration to wait for async tasks to finish in seconds (can be <code>0</code>)
	 * @param logger
	 *            the logger used for printing informational messages, can be <code>null</code>
	 * @return the number of remaining async tasks that are still running after waiting for the
	 *         specified duration
	 */
	public static int awaitAsyncTasksCompletion(
			Plugin plugin,
			int asyncTasksTimeoutSeconds,
			@Nullable Logger logger
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.isTrue(asyncTasksTimeoutSeconds >= 0, "asyncTasksTimeoutSeconds cannot be negative");

		int activeAsyncTasks = getActiveAsyncTasks(plugin);
		if (activeAsyncTasks > 0 && asyncTasksTimeoutSeconds > 0) {
			if (logger != null) {
				logger.info("Waiting up to " + asyncTasksTimeoutSeconds + " seconds for "
						+ activeAsyncTasks + " remaining async tasks to finish ...");
			}

			final long asyncTasksTimeoutMillis = TimeUnit.SECONDS.toMillis(asyncTasksTimeoutSeconds);
			final long waitStartNanos = System.nanoTime();
			long waitDurationMillis = 0L;
			do {
				// Periodically check again:
				try {
					Thread.sleep(25L);
				} catch (InterruptedException e) {
					// Ignore, but reset interrupt flag:
					Thread.currentThread().interrupt();
				}
				// Update the number of active async task before breaking from loop:
				activeAsyncTasks = getActiveAsyncTasks(plugin);

				// Update waiting duration and compare to timeout:
				waitDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartNanos);
				if (waitDurationMillis > asyncTasksTimeoutMillis) {
					// Timeout reached, abort waiting..
					break;
				}
			} while (activeAsyncTasks > 0);

			if (waitDurationMillis > 1 && logger != null) {
				logger.info("Waited " + waitDurationMillis + " ms for async tasks to finish.");
			}
		}

		if (activeAsyncTasks > 0 && logger != null) {
			// Severe, since this can potentially result in data loss, depending on what the tasks
			// are doing:
			logger.severe("There are still " + activeAsyncTasks
					+ " remaining async tasks active! Disabling anyway now.");
		}
		return activeAsyncTasks;
	}

	/**
	 * Adapts a {@link BukkitTask} to our {@link ScheduledTask} interface.
	 */
	private static final class BukkitTaskAdapter implements ScheduledTask {

		private final BukkitTask task;

		BukkitTaskAdapter(BukkitTask task) {
			assert task != null;
			this.task = task;
		}

		@Override
		public void cancel() {
			task.cancel();
		}

		@Override
		public boolean isCancelled() {
			return task.isCancelled();
		}
	}

	private SchedulerUtils() {
	}
}
