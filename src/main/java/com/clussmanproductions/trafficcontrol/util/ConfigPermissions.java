package com.clussmanproductions.trafficcontrol.util;

import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingGateGate;
import com.clussmanproductions.trafficcontrol.blocks.BlockLampBase;
import com.clussmanproductions.trafficcontrol.blocks.BlockType3BarrierBase;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.GameType;

/**
 * Crossing gates, crossing lamps and type 3 barriers can only be configured by players
 * in creative mode (gamemode 1): configuration menus, sync packets, screwdriver and relay tuner.
 */
public final class ConfigPermissions {

	private ConfigPermissions() {}

	/** Blocks whose configuration is restricted to creative players. */
	public static boolean isCreativeOnly(Block block)
	{
		return block instanceof BlockCrossingGateGate
				|| block instanceof BlockLampBase
				|| block instanceof BlockType3BarrierBase;
	}

	public static boolean canConfigure(EntityPlayer player)
	{
		if (player == null)
		{
			return false;
		}

		if (player instanceof EntityPlayerMP)
		{
			// Server side: trust the actual game mode, not the player's capabilities
			return ((EntityPlayerMP)player).interactionManager.getGameType() == GameType.CREATIVE;
		}

		// Client side: abilities are synced by the server on every game mode change.
		// (isCreative() on the client reads the tab list, which plugins may hide.)
		return player.capabilities.isCreativeMode;
	}
}
