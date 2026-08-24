package dev.ryanhcode.sable.forge.gametest;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle.EntityRidingSubLevelVehicleHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

@GameTestHolder(Sable.MOD_ID)
public final class PhysicsTest {
    private PhysicsTest() {
    }

    /** Runtime proof for the admitted production Entity.move collision redirect. */
    @GameTest(template = "gravity")
    public static void testEntitySubLevelTracking(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final Vector3d origin = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 6.0, 2.5));
        final ServerSubLevel subLevel = SableTestHelper.spawnSingleBlockSubLevel(
                container, origin, Blocks.STONE.defaultBlockState());
        helper.runAfterDelay(1, () -> {
            final Vector3d blockCenter = subLevel.logicalPose().transformPosition(
                    new Vector3d(subLevel.getMassTracker().getCenterOfMass()));
            final ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
            if (stand == null) {
                helper.fail("Armor stand fixture could not be created");
                return;
            }

            stand.setNoGravity(true);
            stand.setPos(blockCenter.x, blockCenter.y + 0.85, blockCenter.z);
            helper.getLevel().addFreshEntity(stand);
            final double beforeY = stand.getY();
            stand.move(MoverType.SELF, new Vec3(0.0, -0.75, 0.0));

            final EntityMovementExtension movement = (EntityMovementExtension) stand;
            if (movement.sable$getCollisionInfo() == null) {
                helper.fail("Production Entity.move did not enter Sable collision");
                return;
            }
            if (movement.sable$getTrackingSubLevel() != subLevel) {
                helper.fail("Downward sub-level collision did not assign the collided sub-level");
                return;
            }
            if (!stand.verticalCollision || !stand.verticalCollisionBelow || stand.getY() >= beforeY) {
                helper.fail("Sub-level collision flags or clipped movement were not applied");
                return;
            }
            helper.succeed();
        });
    }

    /** Runtime proof for rotated sub-level riding and server-side dismount projection. */
    @GameTest(template = "gravity")
    public static void testRotatedSubLevelRidingAndDismount(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = SableTestHelper.spawnSingleBlockSubLevel(
                container, SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 7.0, 2.5)),
                Blocks.STONE.defaultBlockState());
        final Quaterniond quarterTurn = new Quaterniond().rotateY(Math.PI / 2.0D);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), quarterTurn);
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final Vector3d localVehiclePosition = new Vector3d(
                    subLevel.getMassTracker().getCenterOfMass()).add(0.0D, 1.0D, 0.0D);
            final Minecart vehicle = EntityType.MINECART.create(helper.getLevel());
            final LivingEntity rider = EntityType.PIG.create(helper.getLevel());
            if (vehicle == null || rider == null) {
                helper.fail("Riding fixture entities could not be created");
                return;
            }

            vehicle.setPos(localVehiclePosition.x, localVehiclePosition.y, localVehiclePosition.z);
            rider.setNoGravity(true);
            rider.setPos(localVehiclePosition.x, localVehiclePosition.y, localVehiclePosition.z);
            helper.getLevel().addFreshEntity(vehicle);
            helper.getLevel().addFreshEntity(rider);
            if (Sable.HELPER.getContaining(vehicle) != subLevel) {
                helper.fail("Retained vehicle was not inside the real plot");
                return;
            }
            if (!rider.startRiding(vehicle, true)) {
                helper.fail("Pig could not start riding retained minecart");
                return;
            }

            vehicle.positionRider(rider);
            final Vec3 localFeet = new Vec3(vehicle.getX(),
                    vehicle.getY() + vehicle.getPassengersRidingOffset() + rider.getMyRidingOffset(),
                    vehicle.getZ());
            final Vec3 eyeOffset = rider.getEyePosition().subtract(rider.position());
            final Vec3 expectedRiding = subLevel.logicalPose()
                    .transformPosition(localFeet.add(eyeOffset)).subtract(eyeOffset);
            final double ridingError = rider.position().distanceTo(expectedRiding);
            if (!rider.isPassenger() || rider.getVehicle() != vehicle || ridingError > 1.0E-8D) {
                helper.fail("Rotated riding projection mismatch: expected=" + expectedRiding
                        + ", actual=" + rider.position() + ", error=" + ridingError
                        + ", vehicle=" + vehicle.position() + ", vehicleContaining="
                        + Sable.HELPER.getContaining(vehicle) + ", riderContaining="
                        + Sable.HELPER.getContaining(rider) + ", shouldKick="
                        + EntitySubLevelUtil.shouldKick(rider) + ", directKick="
                        + EntityRidingSubLevelVehicleHelper.kickRidingEntity(rider, localFeet, subLevel)
                        + ", pose=" + subLevel.logicalPose());
                return;
            }

            final Vec3 localDismount = vehicle.getDismountLocationForPassenger(rider);
            final Vec3 expectedDismount = subLevel.logicalPose().transformPosition(localDismount);
            rider.stopRiding();
            final double dismountError = rider.position().distanceTo(expectedDismount);
            if (rider.isPassenger() || dismountError > 1.0E-8D) {
                helper.fail("Rotated dismount projection mismatch: expected=" + expectedDismount
                        + ", actual=" + rider.position() + ", error=" + dismountError);
                return;
            }

            vehicle.discard();
            rider.discard();
            SableTestHelper.removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "continuouscollision")
    public static void testContinuousCollision(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = SableTestHelper.spawnSingleBlockSubLevel(
                container, SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 4, 1.5)),
                Blocks.GLASS.defaultBlockState());
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d impulse = SableTestHelper.absoluteDirection(helper, new Vector3d(0, 10, 20));

        helper.startSequence()
                .thenExecuteAfter(10, () -> handle.applyLinearImpulse(impulse))
                .thenExecuteFor(40, () -> {
                    final Vector3d local = SableTestHelper.localPosition(helper,
                            subLevel.logicalPose().position());
                    if (local.z >= 9) {
                        helper.fail("Sublevel passed through wall",
                                BlockPos.containing(local.x, local.y, local.z));
                    }
                }).thenSucceed();
    }

    /** The upstream test is optional; CP3 deliberately promotes it to required validation. */
    @GameTest(template = "gravity")
    public static void testGravity(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final Vector3dc spawnPosition = SableTestHelper.absolutePosition(
                helper, new Vector3d(2.5, 12, 2.5));
        final ServerSubLevel subLevel = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPosition, Blocks.DIAMOND_BLOCK.defaultBlockState());

        helper.runAfterDelay(1, () -> {
            if (subLevel.isRemoved()) {
                helper.fail("Sublevel was removed");
                return;
            }
            final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
            // GameTest callbacks and Sable's physics event can occur at different points
            // within the server tick. Align the logical pose with the native handle first.
            physicsSystem.updatePose(subLevel);
            final Vector3d startVelocity = handle.getLinearVelocity(new Vector3d());
            final Vector3d startPosition = new Vector3d(subLevel.logicalPose().position());
            helper.runAfterDelay(20, () -> {
                final Vector3dc gravity = DimensionPhysicsData.getGravity(helper.getLevel(), startPosition);
                final double drag = DimensionPhysicsData.getUniversalDrag(helper.getLevel());
                final int substeps = SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK.get();
                final double timeStep = 1.0 / 20.0 / substeps;
                final Vector3d expectedVelocity = new Vector3d(startVelocity);
                final Vector3d expectedDelta = new Vector3d();
                for (int step = 0; step < 20 * substeps; step++) {
                    // The pinned Rapier revision's default IntegrationParameters use four
                    // small-step solver iterations. Gravity and position are integrated in
                    // those iterations; damping is applied once when the outer step is written back.
                    final int solverIterations = 4;
                    final double solverTimeStep = timeStep / solverIterations;
                    for (int solverStep = 0; solverStep < solverIterations; solverStep++) {
                        expectedVelocity.fma(solverTimeStep, gravity);
                        expectedDelta.fma(solverTimeStep, expectedVelocity);
                    }
                    expectedVelocity.div(1.0 + timeStep * drag);
                }
                final Vector3dc velocity = handle.getLinearVelocity(new Vector3d());
                if (!expectedVelocity.equals(velocity, 1e-2)) {
                    helper.fail("Sublevel velocity didn't follow gravity for one measured second: Delta: "
                            + expectedVelocity.distance(velocity));
                    return;
                }
                physicsSystem.updatePose(subLevel);
                final Vector3d actualDelta = subLevel.logicalPose().position()
                        .sub(startPosition, new Vector3d());
                // The GameTest callback can straddle the logical-pose update within one
                // server tick. Keep a 1% displacement tolerance (the upstream test used
                // roughly 10%) while the end velocity remains checked to 1e-2 above.
                final double positionTolerance = Math.max(1e-2, expectedDelta.length() * 0.01);
                if (!expectedDelta.equals(actualDelta, positionTolerance)) {
                    helper.fail("Sublevel position didn't follow gravity for one measured second. Delta: "
                            + expectedDelta.distance(actualDelta) + ", expected=" + expectedDelta
                            + ", actual=" + actualDelta + ", startVelocity=" + startVelocity
                            + ", endVelocity=" + velocity + ", tolerance=" + positionTolerance);
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** Verifies mass/inertia data and an off-centre force through the production Rapier pipeline. */
    @GameTest(template = "gravity")
    public static void testMassAndForce(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = SableTestHelper.spawnSingleBlockSubLevel(
                container, SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 12, 2.5)),
                Blocks.DIAMOND_BLOCK.defaultBlockState());
        final MassData mass = subLevel.getMassTracker();

        // The loaded super_heavy physics definition assigns diamond blocks 4.0 kpg.
        // MassTracker stores its centre in plot block-coordinates, not object-local coordinates.
        if (Math.abs(mass.getMass() - 4.0) > 1e-9 || mass.getCenterOfMass() == null
                || !subLevel.getPlot().contains(mass.getCenterOfMass())) {
            helper.fail("Unexpected diamond-block mass data: mass=" + mass.getMass()
                    + ", center=" + mass.getCenterOfMass());
            return;
        }
        if (!Double.isFinite(mass.getInverseInertiaTensor().m00())
                || mass.getInverseInertiaTensor().m00() <= 0.0) {
            helper.fail("Diamond-block inverse inertia was not finite and positive");
            return;
        }

        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        helper.runAfterDelay(1, () -> {
            final Vector3d initialLinear = handle.getLinearVelocity(new Vector3d());
            final Vector3d initialAngular = handle.getAngularVelocity(new Vector3d());
            final ForceTotal force = new ForceTotal();
            force.applyImpulseAtPoint(mass, new Vector3d(mass.getCenterOfMass()).add(0, 1, 0),
                    new Vector3d(20, 0, 0));
            handle.applyForcesAndReset(force);
            if (force.getLocalForce().lengthSquared() != 0.0 || force.getLocalTorque().lengthSquared() != 0.0) {
                helper.fail("ForceTotal did not reset after application");
                return;
            }
            helper.runAfterDelay(5, () -> {
                final Vector3d linearDelta = handle.getLinearVelocity(new Vector3d()).sub(initialLinear);
                final Vector3d angularDelta = handle.getAngularVelocity(new Vector3d()).sub(initialAngular);
                if (linearDelta.x <= 0.01) {
                    helper.fail("Off-centre force did not produce positive X velocity: " + linearDelta);
                    return;
                }
                if (angularDelta.z >= -0.01) {
                    helper.fail("Off-centre force did not produce negative Z angular velocity: " + angularDelta);
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** Exercises arbitrary boxes, all four constraint surfaces, and rope mutation/removal in one native scene. */
    @GameTest(template = "gravity")
    public static void testConstraintsAndRope(final GameTestHelper helper) {
        final SubLevelPhysicsSystem physicsSystem = requireContainer(helper.getLevel()).physicsSystem();
        final Vector3d firstPosition = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 10, 2.5));
        final Vector3d secondPosition = SableTestHelper.absolutePosition(helper, new Vector3d(4.5, 10, 2.5));
        final BoxPhysicsObject first = boxAt(firstPosition);
        final BoxPhysicsObject second = boxAt(secondPosition);
        final Quaterniond identity = new Quaterniond();
        final Vector3d normal = new Vector3d(1, 0, 0);

        physicsSystem.addObject(first);
        physicsSystem.addObject(second);
        try {
            verifyAndRemoveConstraint(helper, physicsSystem.getPipeline().addConstraint(first, second,
                    new FixedConstraintConfiguration(firstPosition, secondPosition, identity)));
            verifyAndRemoveConstraint(helper, physicsSystem.getPipeline().addConstraint(first, second,
                    new FreeConstraintConfiguration(firstPosition, secondPosition, identity)));

            final GenericConstraintHandle generic = physicsSystem.getPipeline().addConstraint(first, second,
                    new GenericConstraintConfiguration(firstPosition, secondPosition, identity, identity));
            if (generic == null || !generic.isValid()) {
                throw new IllegalStateException("Generic constraint was not created");
            }
            generic.setFrame1(new Vector3d(), identity);
            generic.setFrame2(new Vector3d(), identity);
            generic.setLimit(ConstraintJointAxis.LINEAR_X, -0.25, 0.25);
            generic.lockAxes(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z);
            generic.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, 1.0, 0.1, true, 10.0);
            generic.setContactsEnabled(false);
            generic.getJointImpulses(new Vector3d(), new Vector3d());
            generic.remove();
            if (generic.isValid()) {
                throw new IllegalStateException("Generic constraint stayed valid after removal");
            }

            verifyAndRemoveConstraint(helper, physicsSystem.getPipeline().addConstraint(first, second,
                    new RotaryConstraintConfiguration(firstPosition, secondPosition, normal, normal)));

            final List<Vector3d> points = List.of(
                    SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 8, 4.5)),
                    SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 7, 4.5)),
                    SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 6, 4.5)));
            final RopePhysicsObject rope = new RopePhysicsObject(points, 0.125);
            physicsSystem.addObject(rope);
            if (!rope.isActive()) {
                throw new IllegalStateException("Rope did not become active");
            }
            rope.setAttachment(RopeHandle.AttachmentPoint.START, points.get(0), null);
            rope.setFirstSegmentLength(1.0);
            rope.addPoint(new Vector3d(points.get(0)).add(0, 1, 0));
            rope.updatePose();
            if (rope.getPoints().size() != 4) {
                throw new IllegalStateException("Rope add-point did not preserve native point count");
            }
            rope.removeFirstPoint();
            rope.updatePose();
            if (rope.getPoints().size() != 3) {
                throw new IllegalStateException("Rope remove-point did not preserve native point count");
            }
            physicsSystem.removeObject(rope);
            if (rope.isActive()) {
                throw new IllegalStateException("Rope stayed active after removal");
            }
        } catch (final RuntimeException exception) {
            helper.fail("Physics core surface validation failed: " + exception);
            return;
        } finally {
            physicsSystem.removeObject(first);
            physicsSystem.removeObject(second);
        }
        helper.succeed();
    }

    private static BoxPhysicsObject boxAt(final Vector3dc position) {
        final dev.ryanhcode.sable.companion.math.Pose3d pose =
                new dev.ryanhcode.sable.companion.math.Pose3d();
        pose.position().set(position);
        return new BoxPhysicsObject(pose, new Vector3d(0.25), 1.0);
    }

    private static void verifyAndRemoveConstraint(final GameTestHelper helper,
                                                  final PhysicsConstraintHandle constraint) {
        if (constraint == null || !constraint.isValid()) {
            throw new IllegalStateException("Constraint was not created");
        }
        constraint.setContactsEnabled(false);
        constraint.getJointImpulses(new Vector3d(), new Vector3d());
        constraint.remove();
        if (constraint.isValid()) {
            throw new IllegalStateException("Constraint stayed valid after removal");
        }
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }
}
