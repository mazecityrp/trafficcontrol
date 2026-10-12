package com.clussmanproductions.trafficcontrol.tileentity;

import com.clussmanproductions.trafficcontrol.network.PacketHandler;
import com.clussmanproductions.trafficcontrol.network.PacketSyncableTileEntity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public abstract class SyncableTileEntity extends TileEntity {
	public abstract NBTTagCompound getClientToServerUpdateTag();
	public abstract void handleClientToServerUpdateTag(NBTTagCompound compound);

	/**
	 * Server side: may this player apply a client-to-server update to this tile entity?
	 * Checked before handleClientToServerUpdateTag; the update is dropped when false.
	 */
	public boolean canPlayerUpdate(EntityPlayer player)
	{
		return true;
	}

	public void performClientToServerSync()
	{
		PacketSyncableTileEntity syncPacket = new PacketSyncableTileEntity();
		syncPacket.tileEntityPos = getPos();
		syncPacket.data = getClientToServerUpdateTag();
		
		PacketHandler.INSTANCE.sendToServer(syncPacket);
	}
}
