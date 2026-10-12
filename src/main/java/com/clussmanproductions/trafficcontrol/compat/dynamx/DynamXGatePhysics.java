package com.clussmanproductions.trafficcontrol.compat.dynamx;

import com.clussmanproductions.trafficcontrol.collision.CrossingGateWall;
import com.clussmanproductions.trafficcontrol.collision.GatePhysics;
import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import fr.dynamx.api.physics.BulletShapeType;
import fr.dynamx.api.physics.EnumBulletShapeType;
import fr.dynamx.api.physics.IPhysicsWorld;
import fr.dynamx.common.DynamXContext;
import fr.dynamx.common.entities.PhysicsEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * DynamX integration, only loaded when DynamX is installed (see {@link GatePhysics#init()}).
 * While the wall of a crossing gate is active, a static box with the same footprint is added to
 * the DynamX physics world (server, and clients that simulate their vehicle), so vehicles stop at the gate.
 * Same kind of body as DynamX's own terrain.
 */
public class DynamXGatePhysics implements GatePhysics.Bridge {
	/** Minimum half thickness, more than the arm so that fast vehicles cannot tunnel through it */
	private static final float MIN_HALF_THICKNESS = 0.2F;

	@Override
	public void update(CrossingGateGateTileEntity gate, CrossingGateWall activeWall) {
		World world = gate.getWorld();
		if (world == null || !DynamXContext.usesPhysicsWorld(world)) {
			return;
		}

		IPhysicsWorld physicsWorld = DynamXContext.getPhysicsWorld(world);
		GateBody current = (GateBody)gate.getPhysicsHandle();
		if (current != null && (current.wall != activeWall || current.physicsWorld != physicsWorld)) {
			remove(gate);
			current = null;
		}

		if (current != null || activeWall.isEmpty() || physicsWorld == null) {
			return;
		}

		// Never drop the wall onto a vehicle (the physics engine would eject it): wait until nothing is under the arm
		if (isOccupied(world, activeWall)) {
			return;
		}

		GateBody body = new GateBody(physicsWorld, activeWall);
		gate.setPhysicsHandle(body);
		physicsWorld.schedule(body::create);
	}

	@Override
	public void remove(CrossingGateGateTileEntity gate) {
		GateBody current = (GateBody)gate.getPhysicsHandle();
		if (current == null) {
			return;
		}

		gate.setPhysicsHandle(null);
		current.removed = true;
		current.physicsWorld.schedule(current::destroy);
	}

	private static boolean isOccupied(World world, CrossingGateWall wall) {
		for (Entity entity : world.<Entity>getEntitiesWithinAABB(PhysicsEntity.class, wall.getBounds())) {
			AxisAlignedBB entityBox = entity.getEntityBoundingBox();
			for (AxisAlignedBB box : wall.getBoxes()) {
				if (box.intersects(entityBox)) {
					return true;
				}
			}
		}

		return false;
	}

	/** The physics body of one gate; create and destroy run on the physics thread */
	private static final class GateBody {
		private final IPhysicsWorld physicsWorld;
		private final CrossingGateWall wall;
		private volatile boolean removed;
		private PhysicsRigidBody body;

		private GateBody(IPhysicsWorld physicsWorld, CrossingGateWall wall) {
			this.physicsWorld = physicsWorld;
			this.wall = wall;
		}

		private void create() {
			if (removed || body != null) {
				return;
			}

			float halfHeight = (float)((wall.getTop() - wall.getBottom()) / 2);
			float halfThickness = Math.max(MIN_HALF_THICKNESS, (float)wall.getHalfThickness());
			BoxCollisionShape shape = new BoxCollisionShape(new Vector3f((float)wall.getHalfLength(), halfHeight, halfThickness));

			// Mass 0: static body, like the terrain
			PhysicsRigidBody rigidBody = new PhysicsRigidBody(shape, 0);
			rigidBody.setPhysicsLocation(new Vector3f((float)wall.getCenterX(), (float)wall.getBottom() + halfHeight, (float)wall.getCenterZ()));
			rigidBody.setPhysicsRotation(new Quaternion().fromAngleNormalAxis(wall.getYaw(), Vector3f.UNIT_Y));
			rigidBody.setUserObject(new BulletShapeType<>(EnumBulletShapeType.TERRAIN, this));

			body = rigidBody;
			physicsWorld.addCollisionObject(rigidBody);
		}

		private void destroy() {
			if (body != null) {
				physicsWorld.removeCollisionObject(body);
				body = null;
			}
		}

		@Override
		public String toString() {
			return "Traffic Control crossing gate wall";
		}
	}
}
