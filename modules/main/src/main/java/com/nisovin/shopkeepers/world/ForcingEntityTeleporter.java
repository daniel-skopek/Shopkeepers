package com.nisovin.shopkeepers.world;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.util.bukkit.SchedulerUtils;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Tries to force an entity teleport, bypassing all other plugins (including this plugin).
 * <p>
 * After a change in the Paper server (PR 9937) in MC 1.20.2 to now also call the
 * {@link EntityTeleportEvent} for plugin invoked teleports, all plugins that previously cancelled
 * or modified entity teleports in certain cases (including this plugin itself) can now accidentally
 * cancel or modify plugin invoked entity teleports that were not supposed to be cancelled or
 * modified previously. For now, we restore the previous behavior for all shopkeeper entity
 * teleports by forcing these teleports.
 */
public class ForcingEntityTeleporter implements Listener {

	private final SKShopkeepersPlugin plugin;

	private @Nullable UUID nextTeleportEntityUuid = null;
	private @Nullable Location toLocation = null;

	public ForcingEntityTeleporter(SKShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}

	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	public void onDisable() {
		HandlerList.unregisterAll(this);

		// Reset any pending forced spawn:
		this.resetForcedEntityTeleport();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	void onEntityTeleport(EntityTeleportEvent event) {
		if (nextTeleportEntityUuid == null) return;
		if (this.matchesForcedEntityTeleport(event)) {
			event.setCancelled(false);
			event.setTo(toLocation);
		} else {
			// Unexpected.
			Log.debug(() -> "Forced entity teleporting seems to be out of sync: A forced teleport "
					+ "was requested for entity with unique id " + nextTeleportEntityUuid
					+ ", but a different entity was teleported: "
					+ event.getEntity().getUniqueId());
		}

		this.resetForcedEntityTeleport();
	}

	private boolean matchesForcedEntityTeleport(EntityTeleportEvent event) {
		return event.getEntity().getUniqueId().equals(nextTeleportEntityUuid);
	}

	/**
	 * Teleports the given entity to the given location, trying to bypass any plugins that cancel or
	 * modify any corresponding {@link EntityTeleportEvent}.
	 * 
	 * @param entity
	 *            the entity to teleport
	 * @param toLocation
	 *            the destination location
	 * @return the result of the teleport
	 */
	public boolean teleport(Entity entity, Location toLocation) {
		this.nextTeleportEntityUuid = entity.getUniqueId();
		this.toLocation = toLocation;

		@Nullable CompletableFuture<Boolean> future = SchedulerUtils.teleportAsync(entity, toLocation);
		if (future == null) {
			// The teleport could not be scheduled.
			this.resetForcedEntityTeleport();
			return false;
		}

		// Perform the post-processing once the teleport has completed. For synchronous teleports
		// (on servers without the Folia scheduler API), this runs immediately.
		future.whenComplete((result, exception) -> {
			if (exception != null) {
				Log.debug(() -> "Forcing entity teleport failed: " + exception.getMessage());
			}
			// This reset is required if the teleport did not actually trigger an event (e.g. on
			// Spigot instead of Paper servers):
			this.resetForcedEntityTeleport();
			this.postTeleport(entity);
		});

		// For synchronous teleports, the future is already complete and we can return the result.
		// For asynchronous teleports, we optimistically assume success, since the result is only
		// available asynchronously.
		if (future.isDone()) {
			return Boolean.TRUE.equals(future.getNow(Boolean.FALSE));
		}
		return true;
	}

	private void postTeleport(Entity entity) {
		if (!entity.isValid()) return;

		// MC-44654: For some entities (e.g. end crystals), the server does not automatically send
		// an update packet to the client when their position changes. We force the client-side
		// update here by toggling some other entity property.
		var customNameVisible = entity.isCustomNameVisible();
		entity.setCustomNameVisible(!customNameVisible);
		entity.setCustomNameVisible(customNameVisible);
	}

	/**
	 * Resets any pending forced entity teleport.
	 */
	public void resetForcedEntityTeleport() {
		nextTeleportEntityUuid = null;
		toLocation = null;
	}
}
