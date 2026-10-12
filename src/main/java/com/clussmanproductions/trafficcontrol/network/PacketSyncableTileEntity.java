package com.clussmanproductions.trafficcontrol.network;

import com.clussmanproductions.trafficcontrol.ModTrafficControl;
import com.clussmanproductions.trafficcontrol.tileentity.SyncableTileEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.server.FMLServerHandler;

public class PacketSyncableTileEntity implements IMessage {
	public BlockPos tileEntityPos;
	public NBTTagCompound data;
	@Override
	public void fromBytes(ByteBuf buf) {
		int x = buf.readInt();
		int y = buf.readInt();
		int z = buf.readInt();
		
		NBTTagCompound tag = ByteBufUtils.readTag(buf);
		
		tileEntityPos = new BlockPos(x, y, z);
		data = tag;
	}
	
	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(tileEntityPos.getX());
		buf.writeInt(tileEntityPos.getY());
		buf.writeInt(tileEntityPos.getZ());
		
		ByteBufUtils.writeTag(buf, data);
	}

	public static class Handler implements IMessageHandler<PacketSyncableTileEntity, IMessage>
	{
		@Override
		public IMessage onMessage(PacketSyncableTileEntity message, MessageContext ctx) {
			FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
			return null;
		}
		
		private void handle(PacketSyncableTileEntity message, MessageContext ctx)
		{
			EntityPlayerMP player = ctx.getServerHandler().player;
			World world = player.world;

			// Never let a client packet load a chunk or carry an empty payload
			if (message.data == null || !world.isBlockLoaded(message.tileEntityPos))
			{
				return;
			}

			TileEntity te = world.getTileEntity(message.tileEntityPos);
			if (te instanceof SyncableTileEntity)
			{
				SyncableTileEntity syncable = (SyncableTileEntity)te;
				if (!syncable.canPlayerUpdate(player))
				{
					ModTrafficControl.logger.warn("Rejected {} update at {} from {}: not allowed",
							te.getClass().getSimpleName(), message.tileEntityPos, player.getName());

					// Resend the server state so the sender's client drops the rejected values
					SPacketUpdateTileEntity serverState = te.getUpdatePacket();
					if (serverState != null)
					{
						player.connection.sendPacket(serverState);
					}
					return;
				}

				syncable.handleClientToServerUpdateTag(message.data);
			}
		}
	}
}
