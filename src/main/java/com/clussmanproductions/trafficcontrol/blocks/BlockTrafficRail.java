package com.clussmanproductions.trafficcontrol.blocks;

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

	/**
	 * A rail section either runs straight (with a connector towards each
	 * neighbour that continues the run) or turns.  A turn is never a 90 degree
	 * elbow: the whole block is replaced by a single 45 degree diagonal that
	 * joins the beam line of the arm running along the rail axis to the beam
	 * line of the perpendicular arm.
	 *
	 * LEFT/RIGHT says which side the straight arm arrives from
	 * ({@code facing.rotateYCCW()} / {@code facing.rotateY()}), FRONT/BACK which
	 * side the diagonal leaves through ({@code facing} / its opposite).
	 */
	public enum EnumShape implements IStringSerializable {
		STRAIGHT("straight"),
		STRAIGHT_LEFT("straight_left"),
		STRAIGHT_RIGHT("straight_right"),
		STRAIGHT_BOTH("straight_both"),
		CORNER_LEFT_FRONT("corner_left_front"),
		CORNER_LEFT_BACK("corner_left_back"),
		CORNER_RIGHT_FRONT("corner_right_front"),
		CORNER_RIGHT_BACK("corner_right_back");

		private final String name;

		EnumShape(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return name;
		}

		public boolean isCorner() {
			return ordinal() >= CORNER_LEFT_FRONT.ordinal();
		}
	}

	private static final double PX = 1 / 16D;

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

	/** Steps used to approximate a diagonal with axis aligned collision boxes. */
	private static final int CORNER_STEPS = 8;

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
	 * The diagonal this rail should turn into, or null when it stays straight.
	 *
	 * A corner needs a straight arm on exactly one side along the rail axis (so a
	 * run passing straight through is left alone) plus, on one of the two
	 * perpendicular sides, a rail whose own beam line is the one the diagonal has
	 * to reach.  Only neighbour facings are read, never neighbour shapes, so this
	 * cannot recurse.
	 */
	private static EnumShape getCornerShape(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		EnumFacing left = facing.rotateYCCW();
		EnumFacing right = facing.rotateY();

		boolean armLeft = getRailFacing(world, pos.offset(left)) == facing;
		boolean armRight = getRailFacing(world, pos.offset(right)) == facing;

		if (armLeft == armRight)
		{
			return null;
		}

		EnumFacing front = facing;
		EnumFacing back = facing.getOpposite();

		if (armLeft)
		{
			if (getRailFacing(world, pos.offset(back)) == right)
			{
				return EnumShape.CORNER_LEFT_BACK;
			}

			if (getRailFacing(world, pos.offset(front)) == left)
			{
				return EnumShape.CORNER_LEFT_FRONT;
			}
		}
		else
		{
			if (getRailFacing(world, pos.offset(back)) == left)
			{
				return EnumShape.CORNER_RIGHT_BACK;
			}

			if (getRailFacing(world, pos.offset(front)) == right)
			{
				return EnumShape.CORNER_RIGHT_FRONT;
			}
		}

		return null;
	}

	/**
	 * Whether the neighbour on the given side ends a beam on our own beam line -
	 * either another straight rail of the same facing, or a corner whose diagonal
	 * comes out towards us.
	 */
	private static boolean connects(IBlockAccess world, BlockPos pos, EnumFacing dir, EnumFacing facing) {
		BlockPos neighbourPos = pos.offset(dir);
		EnumFacing neighbourFacing = getRailFacing(world, neighbourPos);

		if (neighbourFacing == null)
		{
			return false;
		}

		if (neighbourFacing == facing)
		{
			return true;
		}

		EnumShape neighbourShape = getCornerShape(world, neighbourPos, neighbourFacing);

		if (neighbourShape == null)
		{
			return false;
		}

		EnumFacing towardsUs = dir.getOpposite();

		switch (neighbourShape)
		{
			case CORNER_LEFT_BACK:
				return neighbourFacing.getOpposite() == towardsUs && facing == neighbourFacing.rotateY();
			case CORNER_LEFT_FRONT:
				return neighbourFacing == towardsUs && facing == neighbourFacing.rotateYCCW();
			case CORNER_RIGHT_BACK:
				return neighbourFacing.getOpposite() == towardsUs && facing == neighbourFacing.rotateYCCW();
			case CORNER_RIGHT_FRONT:
				return neighbourFacing == towardsUs && facing == neighbourFacing.rotateY();
			default:
				return false;
		}
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
		EnumFacing facing = state.getValue(FACING);

		EnumShape corner = getCornerShape(worldIn, pos, facing);
		if (corner != null)
		{
			return state.withProperty(SHAPE, corner);
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

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		IBlockState realState = state.getActualState(source, pos);
		EnumShape shape = realState.getValue(SHAPE);
		EnumFacing facing = realState.getValue(FACING);

		if (shape.isCorner())
		{
			return rotate(CORNER_BOUNDS[shape.ordinal() - EnumShape.CORNER_LEFT_FRONT.ordinal()], facing);
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

		if (!shape.isCorner())
		{
			super.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState);
			return;
		}

		// A single box cannot follow a diagonal, so walk the beam and drop a box
		// on every step of it.
		EnumFacing facing = realState.getValue(FACING);
		double[] line = CORNER_LINES[shape.ordinal() - EnumShape.CORNER_LEFT_FRONT.ordinal()];
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
}
