package com.clussmanproductions.trafficcontrol.collision;

import com.clussmanproductions.trafficcontrol.ModTrafficControl;
import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;

import net.minecraftforge.fml.common.Loader;

/**
 * Bridge to an external physics engine (DynamX) for the invisible wall of crossing gates.
 * Does nothing unless that mod is installed.
 */
public final class GatePhysics {
	public static final String DYNAMX_MODID = "dynamxmod";

	public interface Bridge {
		/** Called every tick: the wall to enforce, {@link CrossingGateWall#EMPTY} while the arm is raised */
		void update(CrossingGateGateTileEntity gate, CrossingGateWall activeWall);

		/** Called when the gate is broken or unloaded */
		void remove(CrossingGateGateTileEntity gate);
	}

	private static final Bridge NONE = new Bridge() {
		@Override
		public void update(CrossingGateGateTileEntity gate, CrossingGateWall activeWall) {
		}

		@Override
		public void remove(CrossingGateGateTileEntity gate) {
		}
	};

	private static Bridge bridge = NONE;

	private GatePhysics() {}

	public static Bridge get() {
		return bridge;
	}

	public static void init() {
		if (!Loader.isModLoaded(DYNAMX_MODID)) {
			return;
		}

		// Loaded by name so that DynamX classes are never touched when DynamX is absent
		try {
			bridge = (Bridge)Class.forName("com.clussmanproductions.trafficcontrol.compat.dynamx.DynamXGatePhysics").newInstance();
			ModTrafficControl.logger.info("DynamX found: vehicles will be stopped by lowered crossing gates");
		}
		catch (Throwable t) {
			ModTrafficControl.logger.error("Could not enable DynamX collisions for crossing gates", t);
		}
	}
}
