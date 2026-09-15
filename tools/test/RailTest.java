import java.util.HashMap;
import java.util.Map;

import com.clussmanproductions.trafficcontrol.blocks.BlockTrafficRail;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.WorldType;
import net.minecraft.tileentity.TileEntity;

/** Drives BlockTrafficRail.getActualState() over hand built layouts. */
public class RailTest {

	static BlockTrafficRail RAIL;
	static IBlockState AIR;

	static class FakeWorld implements IBlockAccess {
		final Map<BlockPos, IBlockState> blocks = new HashMap<BlockPos, IBlockState>();

		FakeWorld put(int x, int y, int z, EnumFacing facing) {
			blocks.put(new BlockPos(x, y, z), RAIL.getDefaultState().withProperty(BlockTrafficRail.FACING, facing));
			return this;
		}

		public IBlockState getBlockState(BlockPos pos) {
			IBlockState state = blocks.get(pos);
			return state == null ? AIR : state;
		}

		public TileEntity getTileEntity(BlockPos pos) { return null; }
		public int getCombinedLight(BlockPos pos, int min) { return 0; }
		public boolean isAirBlock(BlockPos pos) { return blocks.get(pos) == null; }
		public Biome getBiome(BlockPos pos) { return null; }
		public int getStrongPower(BlockPos pos, EnumFacing direction) { return 0; }
		public WorldType getWorldType() { return WorldType.DEFAULT; }
		public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean def) { return false; }
	}

	static int failures = 0;

	static void check(FakeWorld world, int x, int y, int z, String expected) {
		IBlockState state = world.getBlockState(new BlockPos(x, y, z));
		IBlockState actual = state.getBlock().getActualState(state, world, new BlockPos(x, y, z));
		String got = actual.getValue(BlockTrafficRail.SHAPE).getName();
		boolean ok = got.equals(expected);
		if (!ok) failures++;
		System.out.printf("  %-4s (%2d,%2d,%2d) facing=%-5s -> %-16s %s%n",
				ok ? "ok" : "FAIL", x, y, z,
				actual.getValue(BlockTrafficRail.FACING).getName(), got,
				ok ? "" : "(attendu " + expected + ")");
	}

	static void bounds(FakeWorld world, int x, int y, int z) {
		IBlockState state = world.getBlockState(new BlockPos(x, y, z));
		AxisAlignedBB box = state.getBoundingBox(world, new BlockPos(x, y, z));
		System.out.printf("       bbox (%2d,%2d,%2d) = %.3f..%.3f x  %.3f..%.3f y  %.3f..%.3f z%n",
				x, y, z, box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ);
	}

	public static void main(String[] args) throws Exception {
		net.minecraft.init.Bootstrap.register();
		RAIL = new BlockTrafficRail();
		AIR = new Block(Material.AIR).getDefaultState();

		// ---- rampe descendante vers le sud (facing=east, run le long de Z) ----
		System.out.println("rampe : plat, plat, descente de 2 marches, plat, plat");
		FakeWorld ramp = new FakeWorld();
		ramp.put(0, 2, -2, EnumFacing.EAST).put(0, 2, -1, EnumFacing.EAST).put(0, 2, 0, EnumFacing.EAST);
		ramp.put(0, 1, 1, EnumFacing.EAST);
		ramp.put(0, 0, 2, EnumFacing.EAST).put(0, 0, 3, EnumFacing.EAST).put(0, 0, 4, EnumFacing.EAST);
		check(ramp, 0, 2, -1, "straight_both");
		check(ramp, 0, 2, 0, "slope_flat_down");
		check(ramp, 0, 1, 1, "slope_up_down");
		check(ramp, 0, 0, 2, "slope_up_flat");
		check(ramp, 0, 0, 3, "straight_both");
		bounds(ramp, 0, 1, 1);
		bounds(ramp, 0, 0, 2);

		// ---- montee : meme chose vue depuis l'autre bout ----
		System.out.println("montee vers le sud");
		FakeWorld up = new FakeWorld();
		up.put(0, 0, -1, EnumFacing.EAST).put(0, 0, 0, EnumFacing.EAST);
		up.put(0, 1, 1, EnumFacing.EAST);
		up.put(0, 2, 2, EnumFacing.EAST).put(0, 2, 3, EnumFacing.EAST);
		check(up, 0, 0, 0, "slope_flat_up");
		check(up, 0, 1, 1, "slope_down_up");
		check(up, 0, 2, 2, "slope_down_flat");

		// ---- sommet et creux ----
		System.out.println("sommet (descend des deux cotes) et creux");
		FakeWorld peak = new FakeWorld();
		peak.put(0, 0, -1, EnumFacing.EAST).put(0, 1, 0, EnumFacing.EAST).put(0, 0, 1, EnumFacing.EAST);
		check(peak, 0, 1, 0, "slope_down_down");
		FakeWorld dip = new FakeWorld();
		dip.put(0, 1, -1, EnumFacing.EAST).put(0, 0, 0, EnumFacing.EAST).put(0, 1, 1, EnumFacing.EAST);
		check(dip, 0, 0, 0, "slope_up_up");

		// ---- garde anti-empilement : un rail juste au dessus appartient a l'autre marche ----
		System.out.println("rails empiles : pas de pente fantome");
		FakeWorld stack = new FakeWorld();
		stack.put(0, 1, 0, EnumFacing.EAST).put(0, 1, 1, EnumFacing.EAST).put(0, 0, 1, EnumFacing.EAST);
		check(stack, 0, 1, 0, "straight_right");
		check(stack, 0, 0, 1, "straight");

		// ---- coins : non regresses ----
		System.out.println("coins (regression)");
		FakeWorld corner = new FakeWorld();
		corner.put(0, 0, -1, EnumFacing.EAST).put(0, 0, 0, EnumFacing.EAST);
		corner.put(-1, 0, 0, EnumFacing.SOUTH).put(-2, 0, 0, EnumFacing.SOUTH);
		check(corner, 0, 0, 0, "corner_left_back");
		check(corner, -1, 0, 0, "straight_both");
		check(corner, 0, 0, -1, "straight_right");

		FakeWorld cornerLong = new FakeWorld();
		cornerLong.put(0, 0, -1, EnumFacing.EAST).put(0, 0, 0, EnumFacing.EAST);
		cornerLong.put(1, 0, 0, EnumFacing.NORTH).put(2, 0, 0, EnumFacing.NORTH);
		check(cornerLong, 0, 0, 0, "corner_left_front");
		check(cornerLong, 1, 0, 0, "straight_both");

		// ---- le meme coin pose avec l'autre facing donne le meme joint ----
		System.out.println("coin pose avec le facing de l'autre run");
		FakeWorld swapped = new FakeWorld();
		swapped.put(0, 0, -1, EnumFacing.EAST).put(0, 0, 0, EnumFacing.SOUTH);
		swapped.put(-1, 0, 0, EnumFacing.SOUTH).put(-2, 0, 0, EnumFacing.SOUTH);
		check(swapped, 0, 0, 0, "corner_right_back");

		// ---- rail isole ----
		System.out.println("rail isole");
		FakeWorld lone = new FakeWorld();
		lone.put(0, 0, 0, EnumFacing.EAST);
		check(lone, 0, 0, 0, "straight");

		System.out.println(failures == 0 ? "\nTOUT PASSE" : "\n" + failures + " ECHEC(S)");
		System.exit(failures == 0 ? 0 : 1);
	}
}
