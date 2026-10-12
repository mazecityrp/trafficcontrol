package com.clussmanproductions.trafficcontrol.blocks;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.clussmanproductions.trafficcontrol.ModTrafficControl;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;

public class BlockTrafficRail extends Block {
	public static PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
	public static PropertyEnum<EnumShape> SHAPE = PropertyEnum.create("shape", EnumShape.class);

	/** What the run does on one side of a rail: stays level, climbs, or drops. */
	public enum Level {
		FLAT,
		UP,
		DOWN;
	}

	/**
	 * A rail section runs straight (with a connector towards each neighbour that
	 * continues the run), turns, or slopes.
	 *
	 * A turn is never a 90 degree elbow: the whole block becomes a single 45
	 * degree diagonal joining the beam line of the arm running along the rail
	 * axis to the beam line of the perpendicular arm.  LEFT/RIGHT says which side
	 * the straight arm arrives from ({@code facing.rotateYCCW()} /
	 * {@code facing.rotateY()}), FRONT/BACK which side the diagonal leaves
	 * through ({@code facing} / its opposite).
	 *
	 * The same four diagonals also make up a diagonal run - a staircase of rails,
	 * each one block along and one block across from the last - whose blocks pass
	 * the 45 degree line on to each other instead of to a straight arm.  Only the
	 * ends differ: where the beam bends into a straight rail it is mitred past
	 * the block edge, where it carries straight on into the next diagonal (or
	 * stops, at a loose end) it is cut square and flush, since two collinear
	 * pieces that overlap z-fight.  CORNER_* bends at both ends, DIAGONAL_* at
	 * neither, and DIAGONAL_*_BEND_&lt;side&gt; at that side only.
	 *
	 * A slope is two independent halves meeting at the beam centre of the block,
	 * each one flat or tilted 45 degrees towards its own edge, named
	 * SLOPE_&lt;left&gt;_&lt;right&gt;.  Every rail of a staircase run sits one
	 * block along and one block lower than the last, so the beam centres line up
	 * on one continuous 45 degree line and no transition piece is needed:
	 * SLOPE_FLAT_DOWN is the top of a ramp, SLOPE_UP_FLAT the bottom of one, and
	 * SLOPE_UP_DOWN a block in the middle.
	 */
	public enum EnumShape implements IStringSerializable {
		STRAIGHT("straight"),
		STRAIGHT_LEFT("straight_left"),
		STRAIGHT_RIGHT("straight_right"),
		STRAIGHT_BOTH("straight_both"),
		CORNER_LEFT_FRONT("corner_left_front", 0, true, true),
		CORNER_LEFT_BACK("corner_left_back", 1, true, true),
		CORNER_RIGHT_FRONT("corner_right_front", 2, true, true),
		CORNER_RIGHT_BACK("corner_right_back", 3, true, true),
		DIAGONAL_LEFT_FRONT("diagonal_left_front", 0, false, false),
		DIAGONAL_LEFT_FRONT_BEND_LEFT("diagonal_left_front_bend_left", 0, true, false),
		DIAGONAL_LEFT_FRONT_BEND_FRONT("diagonal_left_front_bend_front", 0, false, true),
		DIAGONAL_LEFT_BACK("diagonal_left_back", 1, false, false),
		DIAGONAL_LEFT_BACK_BEND_LEFT("diagonal_left_back_bend_left", 1, true, false),
		DIAGONAL_LEFT_BACK_BEND_BACK("diagonal_left_back_bend_back", 1, false, true),
		DIAGONAL_RIGHT_FRONT("diagonal_right_front", 2, false, false),
		DIAGONAL_RIGHT_FRONT_BEND_RIGHT("diagonal_right_front_bend_right", 2, true, false),
		DIAGONAL_RIGHT_FRONT_BEND_FRONT("diagonal_right_front_bend_front", 2, false, true),
		DIAGONAL_RIGHT_BACK("diagonal_right_back", 3, false, false),
		DIAGONAL_RIGHT_BACK_BEND_RIGHT("diagonal_right_back_bend_right", 3, true, false),
		DIAGONAL_RIGHT_BACK_BEND_BACK("diagonal_right_back_bend_back", 3, false, true),
		SLOPE_FLAT_UP("slope_flat_up", Level.FLAT, Level.UP),
		SLOPE_FLAT_DOWN("slope_flat_down", Level.FLAT, Level.DOWN),
		SLOPE_UP_FLAT("slope_up_flat", Level.UP, Level.FLAT),
		SLOPE_UP_UP("slope_up_up", Level.UP, Level.UP),
		SLOPE_UP_DOWN("slope_up_down", Level.UP, Level.DOWN),
		SLOPE_DOWN_FLAT("slope_down_flat", Level.DOWN, Level.FLAT),
		SLOPE_DOWN_UP("slope_down_up", Level.DOWN, Level.UP),
		SLOPE_DOWN_DOWN("slope_down_down", Level.DOWN, Level.DOWN);

		private final String name;
		private final Level left;
		private final Level right;
		/** Index into CORNER_LINES / CORNER_BOUNDS, or -1. */
		private final int corner;
		private final boolean bendArm;
		private final boolean bendTurn;

		EnumShape(String name) {
			this(name, null, null);
		}

		EnumShape(String name, Level left, Level right) {
			this(name, left, right, -1, false, false);
		}

		EnumShape(String name, int corner, boolean bendArm, boolean bendTurn) {
			this(name, null, null, corner, bendArm, bendTurn);
		}

		EnumShape(String name, Level left, Level right, int corner, boolean bendArm, boolean bendTurn) {
			this.name = name;
			this.left = left;
			this.right = right;
			this.corner = corner;
			this.bendArm = bendArm;
			this.bendTurn = bendTurn;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return name;
		}

		public boolean isSlope() {
			return left != null;
		}

		/** Any of the diagonal pieces, however its ends are cut. */
		public boolean isCorner() {
			return corner >= 0;
		}

		public int getCorner() {
			return corner;
		}

		public Level getLeft() {
			return left;
		}

		public Level getRight() {
			return right;
		}
	}

	private static final double PX = 1 / 16D;

	/** SLOPES[left][right], indexed by {@link Level#ordinal()}; level/level is null. */
	private static final EnumShape[][] SLOPES = new EnumShape[Level.values().length][Level.values().length];

	/** DIAGONALS[corner][bendArm][bendTurn], the booleans as 0/1. */
	private static final EnumShape[][][] DIAGONALS = new EnumShape[4][2][2];

	static {
		for (EnumShape shape : EnumShape.values())
		{
			if (shape.isSlope())
			{
				SLOPES[shape.getLeft().ordinal()][shape.getRight().ordinal()] = shape;
			}

			if (shape.isCorner())
			{
				DIAGONALS[shape.corner][shape.bendArm ? 1 : 0][shape.bendTurn ? 1 : 0] = shape;
			}
		}
	}

	/**
	 * A diagonal through one block in absolute directions: the rail's facing,
	 * the side its arm arrives from (along the rail axis, on the rail's own beam
	 * line) and the side it turns out through (the rail's front or back).
	 */
	private static final class Diagonal {
		final EnumFacing facing;
		final EnumFacing arm;
		final EnumFacing turn;

		Diagonal(EnumFacing facing, EnumFacing arm, EnumFacing turn) {
			this.facing = facing;
			this.arm = arm;
			this.turn = turn;
		}

		/** The diagonal leaving through two adjacent sides, whichever of them is the arm for this facing. */
		static Diagonal through(EnumFacing facing, EnumFacing side, EnumFacing otherSide) {
			return side.getAxis() == facing.getAxis()
					? new Diagonal(facing, otherSide, side)
					: new Diagonal(facing, side, otherSide);
		}

		/**
		 * The facing whose beam line this diagonal reaches on the given side, or
		 * null when it does not leave through it.  On the arm that is the rail's
		 * own; on the turn it is the perpendicular run's, which the long diagonal
		 * (out through the front) meets with that run facing towards the arm and
		 * the short one (out through the back) with it facing away.
		 */
		EnumFacing beamOn(EnumFacing side) {
			if (side == arm)
			{
				return facing;
			}

			if (side == turn)
			{
				return turn == facing ? arm : arm.getOpposite();
			}

			return null;
		}

		EnumFacing otherEnd(EnumFacing side) {
			return side == arm ? turn : arm;
		}

		EnumShape toShape(boolean bendArm, boolean bendTurn) {
			int corner = (arm == facing.rotateYCCW() ? 0 : 2) + (turn == facing ? 0 : 1);
			return DIAGONALS[corner][bendArm ? 1 : 0][bendTurn ? 1 : 0];
		}
	}

	/**
	 * Corner geometry in the facing=east reference frame (the one the models are
	 * authored in), in model pixels: {startX, startZ, endX, endZ} of the beam
	 * centre line, then the outwards normal the flanges stick out along.
	 *
	 * The beam of a straight rail sits at x=6 for facing=east and at z=6 / z=10
	 * for the perpendicular facings, which is why a short corner cuts 6 pixels
	 * off the block corner while the other one crosses 10 pixels of it - both
	 * are exactly 45 degrees.
	 */
	private static final double[][] CORNER_LINES = new double[][] {
		{ 6, 0, 16, 10, -0.70710678, 0.70710678 },   // CORNER_LEFT_FRONT
		{ 6, 0, 0, 6, -0.70710678, -0.70710678 },    // CORNER_LEFT_BACK
		{ 6, 16, 16, 6, -0.70710678, -0.70710678 },  // CORNER_RIGHT_FRONT
		{ 6, 16, 0, 10, -0.70710678, 0.70710678 },   // CORNER_RIGHT_BACK
	};

	/** Overall hull of each corner, facing=east frame. */
	private static final AxisAlignedBB[] CORNER_BOUNDS = new AxisAlignedBB[] {
		new AxisAlignedBB(4.519 * PX, 0, 0, 1, 1.25, 11.481 * PX),      // CORNER_LEFT_FRONT
		new AxisAlignedBB(0, 0, 0, 6.704 * PX, 1.25, 6.704 * PX),       // CORNER_LEFT_BACK
		new AxisAlignedBB(4.519 * PX, 0, 4.519 * PX, 1, 1.25, 1),       // CORNER_RIGHT_FRONT
		new AxisAlignedBB(0, 0, 9.296 * PX, 6.704 * PX, 1.25, 1),       // CORNER_RIGHT_BACK
	};

	/** How far the profile reaches either side of the beam centre line, in pixels. */
	private static final double PROFILE_OUT = 1.6;
	private static final double PROFILE_IN = 0.5;
	/** Half the height of the beam profile, in pixels (it spans y 6 to 16). */
	private static final double PROFILE_HALF = 5;

	/** Beam centre height at the middle of any block, and at the edge of a tilted half. */
	private static final double CENTRE_Y = 11;
	private static final double RAISED_Y = 19;
	private static final double LOWERED_Y = 3;

	/** Steps used to approximate a diagonal with axis aligned collision boxes. */
	private static final int CORNER_STEPS = 8;
	private static final int SLOPE_STEPS = 4;

	public BlockTrafficRail()
	{
		super(Material.IRON);
		setRegistryName("traffic_rail");
		setUnlocalizedName(ModTrafficControl.MODID + ".traffic_rail");
		setHardness(2f);
		setHarvestLevel("pickaxe", 2);
		setCreativeTab(ModTrafficControl.CREATIVE_TAB);
		setDefaultState(blockState.getBaseState()
				.withProperty(FACING, EnumFacing.NORTH)
				.withProperty(SHAPE, EnumShape.STRAIGHT));
	}

	public void initModel()
	{
		ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, new ModelResourceLocation(getRegistryName(), "inventory"));
	}

	@Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face)
	{
        if (face == EnumFacing.UP)
        {
            return BlockFaceShape.UNDEFINED;
        }
        return super.getBlockFaceShape(worldIn, state, pos, face);
    }

	@Override
	public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
		return false;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING, SHAPE);
	}

	/**
	 * The facing of the rail at the given position, or null when there is no rail
	 * there.
	 */
	private static EnumFacing getRailFacing(IBlockAccess world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);

		if (!(state.getBlock() instanceof BlockTrafficRail))
		{
			return null;
		}

		return state.getValue(FACING);
	}

	/**
	 * What the run does on one side: a rail straight across keeps it level, one
	 * a block higher or lower makes that half of the beam climb or drop.
	 *
	 * The check on the block above/below us keeps the two rails of a staircase
	 * step agreeing with each other: if a rail is stacked on top of this one, the
	 * diagonal neighbour is that rail's partner and not ours.
	 */
	private static Level runLevel(IBlockAccess world, BlockPos pos, EnumFacing side, EnumFacing facing) {
		BlockPos neighbour = pos.offset(side);

		if (getRailFacing(world, neighbour) == facing)
		{
			return Level.FLAT;
		}

		if (getRailFacing(world, neighbour.up()) == facing && getRailFacing(world, pos.up()) != facing)
		{
			return Level.UP;
		}

		if (getRailFacing(world, neighbour.down()) == facing && getRailFacing(world, pos.down()) != facing)
		{
			return Level.DOWN;
		}

		return Level.FLAT;
	}

	/**
	 * The facing of a rail at pos that a rail facing the given way can join with:
	 * any rail but one turned back to back with it, like the two sides of a
	 * central reservation, which never connect.
	 */
	private static EnumFacing getPartnerFacing(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		EnumFacing other = getRailFacing(world, pos);
		return other == facing.getOpposite() ? null : other;
	}

	/** The sides partner rails sit on. */
	private static List<EnumFacing> getPartnerSides(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		List<EnumFacing> sides = new ArrayList<EnumFacing>(4);

		for (EnumFacing side : EnumFacing.Plane.HORIZONTAL)
		{
			if (getPartnerFacing(world, pos.offset(side), facing) != null)
			{
				sides.add(side);
			}
		}

		return sides;
	}

	/**
	 * The slope this rail takes, or null when it is level.  A block cannot be
	 * both sloped and turning: a model element carries a single rotation and a
	 * slope already spends it, so the slope wins.
	 */
	private static EnumShape getSlopeShape(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		Level left = runLevel(world, pos, facing.rotateYCCW(), facing);
		Level right = runLevel(world, pos, facing.rotateY(), facing);

		return SLOPES[left.ordinal()][right.ordinal()];
	}

	/**
	 * The diagonal a rail turns into at a joint, or null when it does not turn.
	 *
	 * The end of a straight run comes first, exactly as before diagonal runs
	 * existed: a straight arm on exactly one side along the rail axis (so a run
	 * passing straight through is left alone) plus, on one of the two
	 * perpendicular sides, a rail whose own beam line is the one the diagonal has
	 * to reach.
	 *
	 * Then a step of a diagonal run: a rail with partners on exactly two adjacent
	 * sides, which settle its diagonal - the side on its run axis is the arm, the
	 * other one the turn - and that turns when the rails at both ends can take
	 * the beam up (see {@link #meets}).
	 *
	 * Placed facing either of the two directions one looks in from the road side
	 * - north or east for a run whose road lies to the south-west - the steps of
	 * a run alternate long and short diagonals that all land on one 45 degree
	 * line.
	 */
	private static Diagonal getCorner(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		if (getSlopeShape(world, pos, facing) != null)
		{
			return null;
		}

		EnumFacing left = facing.rotateYCCW();
		EnumFacing right = facing.rotateY();

		boolean armLeft = getRailFacing(world, pos.offset(left)) == facing;
		boolean armRight = getRailFacing(world, pos.offset(right)) == facing;

		if (armLeft && armRight)
		{
			return null;
		}

		if (armLeft != armRight)
		{
			EnumFacing arm = armLeft ? left : right;

			for (EnumFacing turn : new EnumFacing[] { facing.getOpposite(), facing })
			{
				Diagonal diagonal = new Diagonal(facing, arm, turn);

				if (getRailFacing(world, pos.offset(turn)) == diagonal.beamOn(turn))
				{
					return diagonal;
				}
			}
		}

		List<EnumFacing> partners = getPartnerSides(world, pos, facing);

		if (partners.size() == 2 && partners.get(0).getAxis() != partners.get(1).getAxis())
		{
			Diagonal diagonal = Diagonal.through(facing, partners.get(0), partners.get(1));

			if (meets(world, pos, diagonal, diagonal.turn, true) && meets(world, pos, diagonal, diagonal.arm, true))
			{
				return diagonal;
			}
		}

		return null;
	}

	/**
	 * Whether the rail across one end of a diagonal can take its beam up.
	 *
	 * Coming in on that rail's run axis, its own beam line - straight, or the arm
	 * of a diagonal of its own - has to be the one we reach.  Coming in through
	 * its front or back, where only a diagonal leaves, it has to be able to carry
	 * our line straight on: meet our beam, and have no other rail around it that
	 * could make it turn some other way.  A rail placed facing the wrong way would
	 * put its line 4 pixels off ours.
	 *
	 * The check reads the same from either side of the joint, so the steps of a
	 * run each decide on their own and still meet.  That rail's own decision also
	 * hangs on its far end, though, so with lookAhead that end is checked too: a
	 * rail facing the wrong way then leaves the joints before it straight instead
	 * of turning one of them into a diagonal that stops short.
	 */
	private static boolean meets(IBlockAccess world, BlockPos pos, Diagonal diagonal, EnumFacing side,
			boolean lookAhead) {
		BlockPos next = pos.offset(side);
		EnumFacing nextFacing = getPartnerFacing(world, next, diagonal.facing);
		EnumFacing beam = diagonal.beamOn(side);

		if (nextFacing == null)
		{
			return false;
		}

		if (side.getAxis() != nextFacing.getAxis())
		{
			return nextFacing == beam;
		}

		EnumFacing onward = diagonal.otherEnd(side).getOpposite();
		Diagonal step = Diagonal.through(nextFacing, side.getOpposite(), onward);

		if (step.beamOn(side.getOpposite()) != beam
				|| getPartnerFacing(world, next.offset(side), nextFacing) != null
				|| getPartnerFacing(world, next.offset(onward.getOpposite()), nextFacing) != null)
		{
			return false;
		}

		return !lookAhead
				|| getPartnerFacing(world, next.offset(onward), nextFacing) == null
				|| meets(world, next, step, onward, false);
	}

	/**
	 * The diagonal a rail with a single partner takes at the loose end of a
	 * diagonal run, or null.  When that partner turns towards us, the 45 degree
	 * line carries on to our far edge instead of stopping dead against a
	 * straight stub - and must, when it comes in through our front or back,
	 * where nothing else could take it up.
	 *
	 * Coming in on our run axis, though, a plain straight rail takes the beam up
	 * just as well, and that is what a lone perpendicular rail at the corner of a
	 * straight run has always done - so there the line only carries on when the
	 * partner is itself in the middle of a run, between two diagonals.
	 */
	private static Diagonal getLooseEnd(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		List<EnumFacing> partners = getPartnerSides(world, pos, facing);

		if (partners.size() != 1 || getSlopeShape(world, pos, facing) != null)
		{
			return null;
		}

		EnumFacing side = partners.get(0);
		BlockPos previous = pos.offset(side);
		Diagonal before = getCorner(world, previous, getRailFacing(world, previous));
		EnumFacing beam = before == null ? null : before.beamOn(side.getOpposite());

		if (beam == null)
		{
			return null;
		}

		EnumFacing behind = before.otherEnd(side.getOpposite());
		Diagonal end = Diagonal.through(facing, side, behind.getOpposite());

		if (end.beamOn(side) != beam)
		{
			return null;
		}

		if (side.getAxis() != facing.getAxis()
				&& !leavesDiagonally(world, previous.offset(behind), behind.getOpposite()))
		{
			return null;
		}

		return end;
	}

	/**
	 * Whether the rail at pos is a diagonal leaving through the given side,
	 * counting the loose end of a run coming in through its front or back - where
	 * nothing but a diagonal can take the line on, so this never asks the
	 * question again.
	 */
	private static boolean leavesDiagonally(IBlockAccess world, BlockPos pos, EnumFacing side) {
		EnumFacing facing = getRailFacing(world, pos);

		if (facing == null)
		{
			return false;
		}

		Diagonal corner = getCorner(world, pos, facing);

		if (corner != null)
		{
			return corner.beamOn(side) != null;
		}

		List<EnumFacing> partners = getPartnerSides(world, pos, facing);

		return side.getAxis() == facing.getAxis()
				&& partners.size() == 1 && partners.get(0) == side
				&& getLooseEnd(world, pos, facing) != null;
	}

	/** The diagonal this rail turns into, at a joint or at the loose end of a run, or null. */
	private static Diagonal getDiagonal(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		Diagonal corner = getCorner(world, pos, facing);

		return corner != null ? corner : getLooseEnd(world, pos, facing);
	}

	/**
	 * Whether the beam bends where it leaves through the given side - into a
	 * straight rail, rather than carrying straight on into the next diagonal or
	 * stopping at a loose end - which decides how that end is cut.
	 */
	private static boolean bendsAt(IBlockAccess world, BlockPos pos, Diagonal diagonal, EnumFacing side) {
		BlockPos next = pos.offset(side);
		EnumFacing nextFacing = getPartnerFacing(world, next, diagonal.facing);

		return nextFacing != null && getDiagonal(world, next, nextFacing) == null;
	}

	/**
	 * Whether the neighbour on the given side ends a beam on our own beam line -
	 * either another rail of the same facing that does not turn away from us, or
	 * a diagonal whose beam comes out towards us.
	 */
	private static boolean connects(IBlockAccess world, BlockPos pos, EnumFacing dir, EnumFacing facing) {
		BlockPos neighbourPos = pos.offset(dir);
		EnumFacing neighbourFacing = getRailFacing(world, neighbourPos);

		if (neighbourFacing == null)
		{
			return false;
		}

		Diagonal neighbour = getDiagonal(world, neighbourPos, neighbourFacing);

		if (neighbour != null)
		{
			return neighbour.beamOn(dir.getOpposite()) == facing;
		}

		return neighbourFacing == facing;
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
		EnumFacing facing = state.getValue(FACING);

		EnumShape slope = getSlopeShape(worldIn, pos, facing);
		if (slope != null)
		{
			return state.withProperty(SHAPE, slope);
		}

		Diagonal diagonal = getDiagonal(worldIn, pos, facing);
		if (diagonal != null)
		{
			return state.withProperty(SHAPE, diagonal.toShape(
					bendsAt(worldIn, pos, diagonal, diagonal.arm),
					bendsAt(worldIn, pos, diagonal, diagonal.turn)));
		}

		boolean left = connects(worldIn, pos, facing.rotateYCCW(), facing);
		boolean right = connects(worldIn, pos, facing.rotateY(), facing);

		EnumShape shape;
		if (left)
		{
			shape = right ? EnumShape.STRAIGHT_BOTH : EnumShape.STRAIGHT_LEFT;
		}
		else
		{
			shape = right ? EnumShape.STRAIGHT_RIGHT : EnumShape.STRAIGHT;
		}

		return state.withProperty(SHAPE, shape);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(FACING).getHorizontalIndex();
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta));
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
	}

	@Override
	public boolean causesSuffocation(IBlockState state) {
		return false;
	}

	/**
	 * Turns a box written in the facing=east reference frame into the one for the
	 * given facing, the same way the blockstate rotates the models.
	 */
	private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing) {
		for (int i = quarterTurns(facing); i > 0; i--)
		{
			box = new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
		}

		return box;
	}

	private static int quarterTurns(EnumFacing facing) {
		switch (facing)
		{
			case SOUTH:
				return 1;
			case WEST:
				return 2;
			case NORTH:
				return 3;
			default:
				return 0;
		}
	}

	/** Height the beam centre reaches at the edge of a half, in model pixels. */
	private static double edgeHeight(Level level) {
		switch (level)
		{
			case UP:
				return RAISED_Y;
			case DOWN:
				return LOWERED_Y;
			default:
				return CENTRE_Y;
		}
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		IBlockState realState = state.getActualState(source, pos);
		EnumShape shape = realState.getValue(SHAPE);
		EnumFacing facing = realState.getValue(FACING);

		if (shape.isCorner())
		{
			return rotate(CORNER_BOUNDS[shape.getCorner()], facing);
		}

		if (shape.isSlope())
		{
			// A climbing half lifts the beam to y=19 at the block edge, and the
			// profile reaches a little above that again.
			boolean climbs = shape.getLeft() == Level.UP || shape.getRight() == Level.UP;
			return rotate(new AxisAlignedBB(0.25, 0, 0, 0.59375, climbs ? 23 * PX : 1.25, 1), facing);
		}

		double leftAmount = shape == EnumShape.STRAIGHT_LEFT || shape == EnumShape.STRAIGHT_BOTH ? 0.25 : 0;
		double rightAmount = shape == EnumShape.STRAIGHT_RIGHT || shape == EnumShape.STRAIGHT_BOTH ? 0.25 : 0;

		return rotate(new AxisAlignedBB(0.25, 0, 0.25 - leftAmount, 0.59375, 1.25, 0.75 + rightAmount), facing);
	}

	@Override
	public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
			List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
		IBlockState realState = isActualState ? state : state.getActualState(worldIn, pos);
		EnumShape shape = realState.getValue(SHAPE);
		EnumFacing facing = realState.getValue(FACING);

		// A single box cannot follow a diagonal, so walk the beam and drop a box
		// on every step of it.
		if (shape.isCorner())
		{
			addCornerBoxes(shape, facing, pos, entityBox, collidingBoxes);
			return;
		}

		if (shape.isSlope())
		{
			addCollisionBoxToList(pos, entityBox, collidingBoxes,
					rotate(new AxisAlignedBB(6.5 * PX, 0, 6.5 * PX, 9.5 * PX, 1.25, 9.5 * PX), facing));
			addSlopeBoxes(facing, pos, entityBox, collidingBoxes, 0, edgeHeight(shape.getLeft()), 8, CENTRE_Y);
			addSlopeBoxes(facing, pos, entityBox, collidingBoxes, 8, CENTRE_Y, 16, edgeHeight(shape.getRight()));
			return;
		}

		super.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState);
	}

	private void addCornerBoxes(EnumShape shape, EnumFacing facing, BlockPos pos, AxisAlignedBB entityBox,
			List<AxisAlignedBB> collidingBoxes) {
		double[] line = CORNER_LINES[shape.getCorner()];
		double normalX = line[4];
		double normalZ = line[5];

		for (int step = 0; step < CORNER_STEPS; step++)
		{
			double from = (double)step / CORNER_STEPS;
			double to = (double)(step + 1) / CORNER_STEPS;

			double fromX = line[0] + (line[2] - line[0]) * from;
			double fromZ = line[1] + (line[3] - line[1]) * from;
			double toX = line[0] + (line[2] - line[0]) * to;
			double toZ = line[1] + (line[3] - line[1]) * to;

			double minX = Math.min(fromX, toX) + Math.min(normalX * PROFILE_OUT, -normalX * PROFILE_IN);
			double maxX = Math.max(fromX, toX) + Math.max(normalX * PROFILE_OUT, -normalX * PROFILE_IN);
			double minZ = Math.min(fromZ, toZ) + Math.min(normalZ * PROFILE_OUT, -normalZ * PROFILE_IN);
			double maxZ = Math.max(fromZ, toZ) + Math.max(normalZ * PROFILE_OUT, -normalZ * PROFILE_IN);

			AxisAlignedBB box = new AxisAlignedBB(
					Math.max(0, minX * PX), 0, Math.max(0, minZ * PX),
					Math.min(1, maxX * PX), 1.25, Math.min(1, maxZ * PX));

			addCollisionBoxToList(pos, entityBox, collidingBoxes, rotate(box, facing));
		}
	}

	/** Walks one half of a sloped beam, from (z0, y0) to (z1, y1) in model pixels. */
	private void addSlopeBoxes(EnumFacing facing, BlockPos pos, AxisAlignedBB entityBox,
			List<AxisAlignedBB> collidingBoxes, double z0, double y0, double z1, double y1) {
		double runZ = z1 - z0;
		double runY = y1 - y0;
		double length = Math.sqrt(runZ * runZ + runY * runY);

		// Half the profile height, carried perpendicular to the beam.
		double spreadZ = Math.abs(runY / length * PROFILE_HALF);
		double spreadY = Math.abs(runZ / length * PROFILE_HALF);

		for (int step = 0; step < SLOPE_STEPS; step++)
		{
			double from = (double)step / SLOPE_STEPS;
			double to = (double)(step + 1) / SLOPE_STEPS;

			double fromZ = z0 + runZ * from;
			double fromY = y0 + runY * from;
			double toZ = z0 + runZ * to;
			double toY = y0 + runY * to;

			AxisAlignedBB box = new AxisAlignedBB(
					4.4 * PX,
					Math.max(0, (Math.min(fromY, toY) - spreadY) * PX),
					Math.max(0, (Math.min(fromZ, toZ) - spreadZ) * PX),
					6.5 * PX,
					(Math.max(fromY, toY) + spreadY) * PX,
					Math.min(1, (Math.max(fromZ, toZ) + spreadZ) * PX));

			addCollisionBoxToList(pos, entityBox, collidingBoxes, rotate(box, facing));
		}
	}
}
