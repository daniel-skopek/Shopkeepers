package com.nisovin.shopkeepers.util.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;

/**
 * Provides access to the Folia scheduler API via reflection.
 * <p>
 * The main module is compiled against the Spigot API, which does not provide the Folia scheduler
 * API. In order to also keep running on non-Folia servers without linkage errors, we therefore only
 * access the Folia scheduler API reflectively.
 * <p>
 * Note: The Folia schedulers schedule {@link Consumer Consumers} of a {@code ScheduledTask},
 * whereas the rest of this plugin schedules plain {@link Runnable Runnables}. We therefore adapt
 * the {@link Runnable} tasks to {@link Consumer Consumers} that simply ignore the provided
 * {@code ScheduledTask}.
 */
final class FoliaScheduler {

	private static final boolean AVAILABLE;
	private static final @Nullable Object GLOBAL_REGION_SCHEDULER;
	private static final @Nullable Object REGION_SCHEDULER;
	private static final @Nullable Object ASYNC_SCHEDULER;
	private static final @Nullable Method IS_GLOBAL_TICK_THREAD;
	private static final @Nullable Method ENTITY_GET_SCHEDULER;

	private static final @Nullable Method GLOBAL_RUN;
	private static final @Nullable Method GLOBAL_RUN_DELAYED;
	private static final @Nullable Method GLOBAL_RUN_AT_FIXED_RATE;

	private static final @Nullable Method REGION_RUN;
	private static final @Nullable Method REGION_RUN_DELAYED;
	private static final @Nullable Method REGION_RUN_AT_FIXED_RATE;

	private static final @Nullable Method ASYNC_RUN_NOW;

	private static final @Nullable Method ENTITY_RUN;
	private static final @Nullable Method ENTITY_RUN_DELAYED;
	private static final @Nullable Method ENTITY_RUN_AT_FIXED_RATE;

	private static final @Nullable Method TASK_CANCEL;
	private static final @Nullable Method TASK_IS_CANCELLED;
	private static final @Nullable Method ENTITY_TELEPORT_ASYNC;

	static {
		boolean available = true;
		Object globalRegionScheduler = null;
		Object regionScheduler = null;
		Object asyncScheduler = null;
		Method isGlobalTickThread = null;
		Method entityGetScheduler = null;
		Method globalRun = null;
		Method globalRunDelayed = null;
		Method globalRunAtFixedRate = null;
		Method regionRun = null;
		Method regionRunDelayed = null;
		Method regionRunAtFixedRate = null;
		Method asyncRunNow = null;
		Method entityRun = null;
		Method entityRunDelayed = null;
		Method entityRunAtFixedRate = null;
		Method taskCancel = null;
		Method taskIsCancelled = null;
		Method entityTeleportAsync = null;

		try {
			Class<?> scheduledTaskClass = Class.forName(
					"io.papermc.paper.threadedregions.scheduler.ScheduledTask"
			);
			Class<?> globalRegionSchedulerClass = Class.forName(
					"io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler"
			);
			Class<?> regionSchedulerClass = Class.forName(
					"io.papermc.paper.threadedregions.scheduler.RegionScheduler"
			);
			Class<?> asyncSchedulerClass = Class.forName(
					"io.papermc.paper.threadedregions.scheduler.AsyncScheduler"
			);
			Class<?> entitySchedulerClass = Class.forName(
					"io.papermc.paper.threadedregions.scheduler.EntityScheduler"
			);

			globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
			regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
			asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);

			// Paper's Server interface provides #isGlobalTickThread:
			isGlobalTickThread = Bukkit.getServer().getClass().getMethod("isGlobalTickThread");
			entityGetScheduler = Entity.class.getMethod("getScheduler");

			globalRun = globalRegionSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
			globalRunDelayed = globalRegionSchedulerClass.getMethod(
					"runDelayed", Plugin.class, Consumer.class, long.class);
			globalRunAtFixedRate = globalRegionSchedulerClass.getMethod(
					"runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

			regionRun = regionSchedulerClass.getMethod(
					"run", Plugin.class, World.class, int.class, int.class, Consumer.class);
			regionRunDelayed = regionSchedulerClass.getMethod(
					"runDelayed", Plugin.class, World.class, int.class, int.class, Consumer.class,
					long.class);
			regionRunAtFixedRate = regionSchedulerClass.getMethod(
					"runAtFixedRate", Plugin.class, World.class, int.class, int.class,
					Consumer.class, long.class, long.class);

			asyncRunNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);

			entityRun = entitySchedulerClass.getMethod(
					"run", Plugin.class, Consumer.class, Runnable.class);
			entityRunDelayed = entitySchedulerClass.getMethod(
					"runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
			entityRunAtFixedRate = entitySchedulerClass.getMethod(
					"runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class,
					long.class);

			taskCancel = scheduledTaskClass.getMethod("cancel");
			taskIsCancelled = scheduledTaskClass.getMethod("isCancelled");

			// Paper's Entity interface provides #teleportAsync:
			entityTeleportAsync = Entity.class.getMethod("teleportAsync", Location.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException
				| IllegalAccessException | InvocationTargetException e) {
			// The Folia scheduler API is not available.
			available = false;
		}

		AVAILABLE = available;
		GLOBAL_REGION_SCHEDULER = globalRegionScheduler;
		REGION_SCHEDULER = regionScheduler;
		ASYNC_SCHEDULER = asyncScheduler;
		IS_GLOBAL_TICK_THREAD = isGlobalTickThread;
		ENTITY_GET_SCHEDULER = entityGetScheduler;
		GLOBAL_RUN = globalRun;
		GLOBAL_RUN_DELAYED = globalRunDelayed;
		GLOBAL_RUN_AT_FIXED_RATE = globalRunAtFixedRate;
		REGION_RUN = regionRun;
		REGION_RUN_DELAYED = regionRunDelayed;
		REGION_RUN_AT_FIXED_RATE = regionRunAtFixedRate;
		ASYNC_RUN_NOW = asyncRunNow;
		ENTITY_RUN = entityRun;
		ENTITY_RUN_DELAYED = entityRunDelayed;
		ENTITY_RUN_AT_FIXED_RATE = entityRunAtFixedRate;
		TASK_CANCEL = taskCancel;
		TASK_IS_CANCELLED = taskIsCancelled;
		ENTITY_TELEPORT_ASYNC = entityTeleportAsync;
	}

	public static boolean isAvailable() {
		return AVAILABLE;
	}

	public static boolean isGlobalTickThread() {
		assert AVAILABLE;
		try {
			Object result = Unsafe.assertNonNull(IS_GLOBAL_TICK_THREAD).invoke(Bukkit.getServer());
			return Unsafe.assertNonNull(result).equals(Boolean.TRUE);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return false;
		}
	}

	// Adapts a Runnable to a reflective Consumer that ignores the provided ScheduledTask.
	private static Consumer<Object> adaptedTask(Runnable task) {
		return (scheduledTask) -> task.run();
	}

	/**
	 * Teleports the given entity to the given location asynchronously.
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
		assert AVAILABLE;
		try {
			Object result = Unsafe.assertNonNull(ENTITY_TELEPORT_ASYNC).invoke(entity, location);
			return Unsafe.cast(result);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
	}

	private static @Nullable ScheduledTask invoke(Method method, @Nullable Object scheduler, Object... args) {
		assert AVAILABLE;
		Object result;
		try {
			result = method.invoke(scheduler, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
		if (result == null) return null;
		return new ReflectedTask(result);
	}

	// GLOBAL

	public static @Nullable ScheduledTask run(Plugin plugin, Runnable task) {
		return invoke(Unsafe.assertNonNull(GLOBAL_RUN), GLOBAL_REGION_SCHEDULER,
				plugin, adaptedTask(task));
	}

	public static @Nullable ScheduledTask runDelayed(Plugin plugin, Runnable task, long delay) {
		return invoke(Unsafe.assertNonNull(GLOBAL_RUN_DELAYED), GLOBAL_REGION_SCHEDULER,
				plugin, adaptedTask(task), Long.valueOf(delay));
	}

	public static @Nullable ScheduledTask runAtFixedRate(
			Plugin plugin,
			Runnable task,
			long delay,
			long period
	) {
		return invoke(Unsafe.assertNonNull(GLOBAL_RUN_AT_FIXED_RATE), GLOBAL_REGION_SCHEDULER,
				plugin, adaptedTask(task), Long.valueOf(delay), Long.valueOf(period));
	}

	// REGION

	public static @Nullable ScheduledTask run(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task
	) {
		return invoke(Unsafe.assertNonNull(REGION_RUN), REGION_SCHEDULER,
				plugin, world, Integer.valueOf(chunkX), Integer.valueOf(chunkZ),
				adaptedTask(task));
	}

	public static @Nullable ScheduledTask runDelayed(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task,
			long delay
	) {
		return invoke(Unsafe.assertNonNull(REGION_RUN_DELAYED), REGION_SCHEDULER,
				plugin, world, Integer.valueOf(chunkX), Integer.valueOf(chunkZ),
				adaptedTask(task), Long.valueOf(delay));
	}

	public static @Nullable ScheduledTask runAtFixedRate(
			Plugin plugin,
			World world,
			int chunkX,
			int chunkZ,
			Runnable task,
			long delay,
			long period
	) {
		return invoke(Unsafe.assertNonNull(REGION_RUN_AT_FIXED_RATE), REGION_SCHEDULER,
				plugin, world, Integer.valueOf(chunkX), Integer.valueOf(chunkZ),
				adaptedTask(task), Long.valueOf(delay), Long.valueOf(period));
	}

	// ENTITY

	public static @Nullable ScheduledTask run(Plugin plugin, Entity entity, Runnable task) {
		return runEntity(Unsafe.assertNonNull(ENTITY_RUN), plugin, entity, task, null, null);
	}

	public static @Nullable ScheduledTask runDelayed(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delay
	) {
		return runEntity(Unsafe.assertNonNull(ENTITY_RUN_DELAYED), plugin, entity, task,
				Long.valueOf(delay), null);
	}

	public static @Nullable ScheduledTask runAtFixedRate(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delay,
			long period
	) {
		return runEntity(Unsafe.assertNonNull(ENTITY_RUN_AT_FIXED_RATE), plugin, entity, task,
				Long.valueOf(delay), Long.valueOf(period));
	}

	private static @Nullable ScheduledTask runEntity(
			Method method,
			Plugin plugin,
			Entity entity,
			Runnable task,
			@Nullable Long delay,
			@Nullable Long period
	) {
		assert AVAILABLE;
		Object entityScheduler;
		try {
			entityScheduler = Unsafe.assertNonNull(ENTITY_GET_SCHEDULER).invoke(entity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
		// Retired callback: Ignored.
		Runnable retired = () -> {
		};
		Consumer<Object> adaptedTask = adaptedTask(task);
		Object result;
		try {
			if (period != null) {
				result = method.invoke(entityScheduler, plugin, adaptedTask, retired, delay, period);
			} else if (delay != null) {
				result = method.invoke(entityScheduler, plugin, adaptedTask, retired, delay);
			} else {
				result = method.invoke(entityScheduler, plugin, adaptedTask, retired);
			}
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
		if (result == null) return null;
		return new ReflectedTask(result);
	}

	// ASYNC

	public static @Nullable ScheduledTask runAsyncNow(
			Plugin plugin,
			Consumer<? super ScheduledTask> task
	) {
		assert AVAILABLE;
		// The async task is a Consumer<ScheduledTask>. We adapt it to a reflective Consumer that
		// wraps the provided ScheduledTask.
		Consumer<Object> adaptedTask = (foliaTask) -> {
			task.accept(new ReflectedTask(foliaTask));
		};
		Object result;
		try {
			result = Unsafe.assertNonNull(ASYNC_RUN_NOW).invoke(ASYNC_SCHEDULER, plugin, adaptedTask);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
		if (result == null) return null;
		return new ReflectedTask(result);
	}

	/**
	 * Adapts a reflected Folia {@code ScheduledTask} to our {@link ScheduledTask} interface.
	 */
	private static final class ReflectedTask implements ScheduledTask {

		private final Object task;

		ReflectedTask(Object task) {
			assert task != null;
			this.task = task;
		}

		@Override
		public void cancel() {
			try {
				Unsafe.assertNonNull(TASK_CANCEL).invoke(task);
			} catch (IllegalAccessException | InvocationTargetException e) {
				// Ignored.
			}
		}

		@Override
		public boolean isCancelled() {
			try {
				Object result = Unsafe.assertNonNull(TASK_IS_CANCELLED).invoke(task);
				return Unsafe.assertNonNull(result).equals(Boolean.TRUE);
			} catch (IllegalAccessException | InvocationTargetException e) {
				return false;
			}
		}
	}

	private FoliaScheduler() {
	}
}
