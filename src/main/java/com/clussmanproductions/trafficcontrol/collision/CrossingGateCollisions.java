package com.clussmanproductions.trafficcontrol.collision;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Makes the invisible wall of lowered crossing gates solid for players, mobs and every other entity
 * moved by Minecraft. Forge asks for extra collision boxes on each collision query (client and server),
 * so the wall can reach far beyond the gate block, follow the arm and needs no block in the world.
 */
@EventBusSubscriber
public class CrossingGateCollisions {
	// Loaded gates per world; each list is only modified by the thread of its world
	private static final Map<World, List<CrossingGateGateTileEntity>> GATES = new ConcurrentHashMap<>();

	public static void register(CrossingGateGateTileEntity gate) {
		List<CrossingGateGateTileEntity> gates = GATES.computeIfAbsent(gate.getWorld(), world -> new CopyOnWriteArrayList<>());
		if (!gates.contains(gate)) {
			gates.add(gate);
		}
	}

	public static void unregister(CrossingGateGateTileEntity gate) {
		World world = gate.getWorld();
		if (world == null) {
			return;
		}

		List<CrossingGateGateTileEntity> gates = GATES.get(world);
		if (gates != null) {
			gates.remove(gate);
			if (gates.isEmpty()) {
				GATES.remove(world, gates);
			}
		}
	}

	@SubscribeEvent
	public static void onGetCollisionBoxes(GetCollisionBoxesEvent e) {
		if (GATES.isEmpty()) {
			return;
		}

		List<CrossingGateGateTileEntity> gates = GATES.get(e.getWorld());
		if (gates == null) {
			return;
		}

		AxisAlignedBB query = e.getAabb();
		for (CrossingGateGateTileEntity gate : gates) {
			CrossingGateWall wall = gate.getActiveWall();
			if (wall.isEmpty() || !wall.getBounds().intersects(query)) {
				continue;
			}

			for (AxisAlignedBB box : wall.getBoxes()) {
				if (box.intersects(query)) {
					e.getCollisionBoxesList().add(box);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onWorldUnload(WorldEvent.Unload e) {
		GATES.remove(e.getWorld());
	}
}
