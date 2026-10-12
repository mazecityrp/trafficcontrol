package com.clussmanproductions.trafficcontrol.collision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Invisible wall under the lowered arm of a crossing gate: it runs from the ground up to the arm,
 * and is at least {@link #MIN_HEIGHT} tall so that nobody can jump over it (like a fence or a wall).
 * Immutable: rebuilt by the tile entity when its configuration or the ground under the arm changes.
 */
public final class CrossingGateWall {
	/** Minimum height of the wall above the ground */
	public static final double MIN_HEIGHT = 1.5;
	/** Minimum half thickness of the wall, used while the arm is thinner */
	public static final double MIN_HALF_THICKNESS = 0.125;
	private static final double STEP = 0.25;
	private static final int MAX_GROUND_SCAN = 6;
	private static final float MAX_LENGTH = 64;

	public static final CrossingGateWall EMPTY = new CrossingGateWall(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, MIN_HALF_THICKNESS, Collections.emptyList());

	// Configuration this wall was built for
	private final float facing;
	private final float length;
	private final float lowerRotationLimit;
	private final float gateHeight;
	private final float gateThickness;

	private final double halfThickness;
	private final List<AxisAlignedBB> boxes;
	private final AxisAlignedBB bounds;

	// The same wall as one oriented box, for physics engines
	private final double centerX;
	private final double centerZ;
	private final double halfLength;
	private final double bottom;
	private final double top;

	private CrossingGateWall(float facing, float length, float lowerRotationLimit, float gateHeight, float gateThickness, double halfThickness, List<AxisAlignedBB> boxes) {
		this.facing = facing;
		this.length = length;
		this.lowerRotationLimit = lowerRotationLimit;
		this.gateHeight = gateHeight;
		this.gateThickness = gateThickness;
		this.halfThickness = halfThickness;
		this.boxes = Collections.unmodifiableList(boxes);

		if (boxes.isEmpty()) {
			bounds = null;
			centerX = centerZ = halfLength = bottom = top = 0;
			return;
		}

		AxisAlignedBB union = boxes.get(0);
		for (AxisAlignedBB box : boxes) {
			union = union.union(box);
		}
		bounds = union;

		AxisAlignedBB first = boxes.get(0);
		AxisAlignedBB last = boxes.get(boxes.size() - 1);
		double firstX = (first.minX + first.maxX) / 2, firstZ = (first.minZ + first.maxZ) / 2;
		double lastX = (last.minX + last.maxX) / 2, lastZ = (last.minZ + last.maxZ) / 2;
		centerX = (firstX + lastX) / 2;
		centerZ = (firstZ + lastZ) / 2;
		halfLength = Math.sqrt((lastX - firstX) * (lastX - firstX) + (lastZ - firstZ) * (lastZ - firstZ)) / 2 + halfThickness;
		bottom = union.minY;
		top = union.maxY;
	}

	/**
	 * Builds the wall of the gate's arm in its closed position.
	 * The arm geometry follows {@link com.clussmanproductions.trafficcontrol.tileentity.render.RendererCrossingGateGate}:
	 * pivot 3px forward and 2px above the block centre, arm from 7.5px to (16 * length + 13)px from the pivot
	 * along -X, its top 7.5px below the pivot, as tall (Y) and thick (Z) as configured, then turned by the block's facing.
	 */
	public static CrossingGateWall compute(CrossingGateGateTileEntity gate) {
		World world = gate.getWorld();
		BlockPos pos = gate.getPos();
		float facing = gate.getFacingRotation();
		float length = gate.getCrossingGateLength();
		float lowerRotationLimit = gate.getLowerRotationLimit();
		float gateHeight = gate.getGateHeight();
		float gateThickness = gate.getGateThickness();

		double facingRad = Math.toRadians(facing);
		double tiltRad = Math.toRadians(-lowerRotationLimit);
		double cosF = Math.cos(facingRad), sinF = Math.sin(facingRad);
		double cosT = Math.cos(tiltRad), sinT = Math.sin(tiltRad);

		double armStart = 7.5 / 16;
		double armEnd = (16 * MathHelper.clamp(length, 0, MAX_LENGTH) + 13) / 16;
		double armCenterY = -(7.5 + gateHeight / 2) / 16;
		double armHalfHeight = gateHeight / 32;
		// Never thinner than the arm itself
		double halfThickness = Math.max(MIN_HALF_THICKNESS, gateThickness / 32);

		List<AxisAlignedBB> boxes = new ArrayList<>();
		int steps = (int)Math.ceil((armEnd - armStart) / STEP);
		for (int i = 0; i <= steps; i++) {
			double s = Math.min(armStart + i * STEP, armEnd);

			double localX = 3.0 / 16 - s * cosT - armCenterY * sinT;
			double localY = 2.0 / 16 - s * sinT + armCenterY * cosT;
			double x = pos.getX() + 0.5 + localX * cosF;
			double z = pos.getZ() + 0.5 - localX * sinF;

			// The gate's own column already collides
			if (MathHelper.floor(x) == pos.getX() && MathHelper.floor(z) == pos.getZ()) {
				continue;
			}

			double armY = pos.getY() + 0.5 + localY;
			double armBottom = armY - armHalfHeight;
			double armTop = armY + armHalfHeight;

			double ground = findGround(world, x, armBottom, z);
			double wallBottom = Double.isNaN(ground) ? armBottom - MIN_HEIGHT : Math.min(ground, armBottom);
			double wallTop = Math.max(armTop, wallBottom + MIN_HEIGHT);

			boxes.add(new AxisAlignedBB(x - halfThickness, wallBottom, z - halfThickness, x + halfThickness, wallTop, z + halfThickness));
		}

		return new CrossingGateWall(facing, length, lowerRotationLimit, gateHeight, gateThickness, halfThickness, boxes);
	}

	/** Top of the first block with a collision box under the arm, or NaN if there is none close enough (or not loaded) */
	private static double findGround(World world, double x, double fromY, double z) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int columnX = MathHelper.floor(x);
		int columnZ = MathHelper.floor(z);
		int startY = MathHelper.floor(fromY);

		for (int y = startY; y > startY - MAX_GROUND_SCAN && y >= 0; y--) {
			cursor.setPos(columnX, y, columnZ);
			if (!world.isBlockLoaded(cursor)) {
				return Double.NaN;
			}

			IBlockState state = world.getBlockState(cursor);
			AxisAlignedBB collision = state.getCollisionBoundingBox(world, cursor);
			if (collision != null) {
				return y + collision.maxY;
			}
		}

		return Double.NaN;
	}

	/** True if this wall was built for this configuration of the gate */
	public boolean matches(float facing, float length, float lowerRotationLimit, float gateHeight, float gateThickness) {
		return Float.compare(this.facing, facing) == 0
				&& Float.compare(this.length, length) == 0
				&& Float.compare(this.lowerRotationLimit, lowerRotationLimit) == 0
				&& Float.compare(this.gateHeight, gateHeight) == 0
				&& Float.compare(this.gateThickness, gateThickness) == 0;
	}

	public boolean isEmpty() {
		return boxes.isEmpty();
	}

	public List<AxisAlignedBB> getBoxes() {
		return boxes;
	}

	/** Union of all boxes, null when empty */
	public AxisAlignedBB getBounds() {
		return bounds;
	}

	public double getCenterX() {
		return centerX;
	}

	public double getCenterZ() {
		return centerZ;
	}

	public double getHalfLength() {
		return halfLength;
	}

	public double getHalfThickness() {
		return halfThickness;
	}

	public double getBottom() {
		return bottom;
	}

	public double getTop() {
		return top;
	}

	/** Rotation of the wall around the Y axis, in radians (its length runs along the rotated X axis) */
	public float getYaw() {
		return (float)Math.toRadians(facing);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CrossingGateWall)) {
			return false;
		}
		CrossingGateWall other = (CrossingGateWall)o;
		return matches(other.facing, other.length, other.lowerRotationLimit, other.gateHeight, other.gateThickness) && boxes.equals(other.boxes);
	}

	@Override
	public int hashCode() {
		return boxes.hashCode();
	}
}
