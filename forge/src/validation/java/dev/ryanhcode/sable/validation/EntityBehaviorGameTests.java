package dev.ryanhcode.sable.validation;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.validation.mixin.BellBlockEntityValidationAccessor;
import dev.ryanhcode.sable.validation.mixin.LevelBlockEntityTickerValidationAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.extensions.IForgeEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.joml.Quaterniond;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Sable.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityBehaviorGameTests {
    private EntityBehaviorGameTests() {
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelProjectileLaunch(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 7.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        final Quaterniond quarterTurn = new Quaterniond().rotateY(Math.PI / 2.0D);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), quarterTurn);
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final Vector3d localVehiclePosition = new Vector3d(
                    subLevel.getMassTracker().getCenterOfMass()).add(0.0D, 1.0D, 0.0D);
            final Minecart vehicle = EntityType.MINECART.create(helper.getLevel());
            final LivingEntity shooter = EntityType.PIG.create(helper.getLevel());
            if (vehicle == null || shooter == null) {
                helper.fail("Projectile fixture entities could not be created");
                return;
            }
            vehicle.setPos(localVehiclePosition.x, localVehiclePosition.y, localVehiclePosition.z);
            shooter.setNoGravity(true);
            shooter.setDeltaMovement(Vec3.ZERO);
            shooter.setPos(localVehiclePosition.x, localVehiclePosition.y, localVehiclePosition.z);
            helper.getLevel().addFreshEntity(vehicle);
            helper.getLevel().addFreshEntity(shooter);
            if (!shooter.startRiding(vehicle, true)) {
                helper.fail("Projectile shooter could not ride retained vehicle");
                return;
            }
            vehicle.positionRider(shooter);

            final Vector3d inherited = ((LivingEntityMovementExtension) shooter).sable$getInheritedVelocity();
            inherited.set(0.25D, 0.5D, -0.75D);
            final Snowball projectile = new Snowball(helper.getLevel(), shooter);
            projectile.shootFromRotation(shooter, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
            final Vector3d rotatedForward = subLevel.logicalPose()
                    .transformNormal(new Vector3d(0.0D, 0.0D, 1.0D));
            final Vec3 expected = new Vec3(rotatedForward.x + inherited.x,
                    rotatedForward.y + inherited.y, rotatedForward.z + inherited.z);
            final double error = projectile.getDeltaMovement().distanceTo(expected);
            if (error > 1.0E-6D) {
                helper.fail("Rotated projectile launch mismatch: expected=" + expected
                        + ", actual=" + projectile.getDeltaMovement() + ", error=" + error);
                return;
            }

            projectile.discard();
            shooter.discard();
            vehicle.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testSubLevelEatBlockGoal(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 7.0D, 2.5D)), Blocks.GRASS_BLOCK.defaultBlockState());
        helper.runAfterDelay(1, () -> {
            final Sheep sheep = EntityType.SHEEP.create(helper.getLevel());
            if (sheep == null) {
                helper.fail("EatBlockGoal sheep fixture could not be created");
                return;
            }
            final Vector3d localFeet = new Vector3d(
                    subLevel.getMassTracker().getCenterOfMass()).add(0.0D, 0.5D, 0.0D);
            final Vector3d globalFeet = subLevel.logicalPose().transformPosition(localFeet);
            sheep.setNoGravity(true);
            sheep.setPos(globalFeet.x, globalFeet.y, globalFeet.z);
            ((EntityMovementExtension) sheep).sable$setTrackingSubLevel(subLevel);
            helper.getLevel().addFreshEntity(sheep);

            final EatBlockGoal goal = new EatBlockGoal(sheep);
            goal.start();
            for (int tick = 0; tick < 40; tick++) {
                goal.tick();
            }
            final var remaining = subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO);
            if (!remaining.is(Blocks.DIRT)) {
                helper.fail("EatBlockGoal did not convert tracked plot grass to dirt: " + remaining);
                return;
            }

            sheep.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelTntInitialVelocity(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 7.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final long seed = 0x5A61E202L;
            helper.getLevel().random.setSeed(seed);
            final PrimedTnt reference = new PrimedTnt(helper.getLevel(), 12.5D, 25.0D, 12.5D, null);
            helper.getLevel().random.setSeed(seed);
            final Vector3d local = new Vector3d(subLevel.getMassTracker().getCenterOfMass()).add(0.0D, 1.0D, 0.0D);
            final PrimedTnt retained = new PrimedTnt(helper.getLevel(), local.x, local.y, local.z, null);
            final Vec3 referenceVelocity = reference.getDeltaMovement();
            final Vector3d expected = subLevel.logicalPose().transformNormalInverse(
                    new Vector3d(referenceVelocity.x, referenceVelocity.y, referenceVelocity.z));
            final Vec3 actual = retained.getDeltaMovement();
            final double error = actual.distanceTo(new Vec3(expected.x, expected.y, expected.z));
            if (error > 1.0E-9D) {
                helper.fail("Retained TNT initial velocity mismatch: expected=" + expected
                        + ", actual=" + actual + ", reference=" + referenceVelocity + ", error=" + error);
                return;
            }
            reference.discard();
            retained.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testSubLevelDispenserRecoil(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)),
                Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.runAfterDelay(1, () -> {
            final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            final Vector3d before = handle.getLinearVelocity(new Vector3d());
            final var center = subLevel.getMassTracker().getCenterOfMass();
            final BlockPos dispenserPos = BlockPos.containing(center.x(), center.y(), center.z());
            final TestProjectileDispenseBehavior behavior = new TestProjectileDispenseBehavior();
            behavior.execute(new BlockSourceImpl(helper.getLevel(), dispenserPos), new ItemStack(Items.SNOWBALL));
            final Vector3d delta = handle.getLinearVelocity(new Vector3d()).sub(before);
            final double error = delta.distance(new Vector3d(-1.5D, 0.0D, 0.0D));
            if (error > 1.0E-6D) {
                helper.fail("Dispenser recoil mismatch: expected=(-1.5,0,0), actual=" + delta
                        + ", error=" + error + ", dispenser=" + dispenserPos);
                return;
            }
            if (behavior.projectile != null) {
                behavior.projectile.discard();
            }
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testArrowHitTransfersImpulseAndLocalizesArrow(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final Vector3d localHit = new Vector3d(subLevel.getMassTracker().getCenterOfMass());
            final Vector3d localArrow = new Vector3d(localHit).add(0.0D, 0.0D, 1.0D);
            final Vector3d globalArrow = subLevel.logicalPose().transformPosition(new Vector3d(localArrow));
            final Vector3d globalVelocity = subLevel.logicalPose().transformNormal(new Vector3d(0.0D, 0.0D, -1.0D));
            final TestArrow arrow = new TestArrow(helper.getLevel());
            arrow.setPos(globalArrow.x, globalArrow.y, globalArrow.z);
            arrow.setDeltaMovement(globalVelocity.x, globalVelocity.y, globalVelocity.z);
            final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            final Vector3d before = handle.getLinearVelocity(new Vector3d());
            arrow.hit(new BlockHitResult(new Vec3(localHit.x, localHit.y, localHit.z),
                    Direction.UP, BlockPos.containing(localHit.x, localHit.y, localHit.z), false));

            final Vec3 expectedDifference = new Vec3(0.0D, 0.0D, -1.0D);
            final Vec3 expectedPosition = new Vec3(localArrow.x, localArrow.y, localArrow.z + 0.05D);
            final Vector3d impulseDelta = handle.getLinearVelocity(new Vector3d()).sub(before);
            final Vector3d expectedImpulseDelta = new Vector3d(globalVelocity)
                    .div(subLevel.getMassTracker().getMass());
            if (arrow.getDeltaMovement().distanceTo(expectedDifference) > 1.0E-8D
                    || arrow.position().distanceTo(expectedPosition) > 1.0E-7D
                    || impulseDelta.distance(expectedImpulseDelta) > 1.0E-6D) {
                helper.fail("Arrow/sublevel hit mismatch: delta=" + arrow.getDeltaMovement()
                        + ", expectedDelta=" + expectedDifference + ", position=" + arrow.position()
                        + ", expectedPosition=" + expectedPosition + ", rigidDelta=" + impulseDelta
                        + ", expectedRigidDelta=" + expectedImpulseDelta);
                return;
            }
            arrow.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testLoyalTridentLeavesRotatedSubLevelBeforeReturning(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final LivingEntity owner = EntityType.PIG.create(helper.getLevel());
            if (owner == null) {
                helper.fail("Loyal trident owner could not be created");
                return;
            }
            owner.setPos(helper.absolutePos(new BlockPos(3, 8, 3)).getCenter());
            final ItemStack stack = new ItemStack(Items.TRIDENT);
            stack.enchant(Enchantments.LOYALTY, 1);
            final TestTrident trident = new TestTrident(helper.getLevel(), owner, stack);
            final Vector3d localPosition = new Vector3d(subLevel.getMassTracker().getCenterOfMass())
                    .add(0.0D, 1.0D, 0.0D);
            final Vec3 localVelocity = new Vec3(0.0D, 0.0D, 0.4D);
            trident.setPos(localPosition.x, localPosition.y, localPosition.z);
            trident.setDeltaMovement(localVelocity);
            ((EntityMovementExtension) trident).sable$setTrackingSubLevel(subLevel);
            trident.setNoPhysics(true);
            trident.armCapture();
            trident.tick();

            final Vector3d expectedPosition = subLevel.logicalPose().transformPosition(new Vector3d(localPosition));
            final Vector3d expectedVelocity = subLevel.logicalPose().transformNormal(
                    new Vector3d(localVelocity.x, localVelocity.y, localVelocity.z));
            if (trident.capturedPosition == null || trident.capturedVelocity == null
                    || trident.capturedPosition.distanceTo(new Vec3(expectedPosition.x, expectedPosition.y,
                    expectedPosition.z)) > 1.0E-7D
                    || trident.capturedVelocity.distanceTo(new Vec3(expectedVelocity.x, expectedVelocity.y,
                    expectedVelocity.z)) > 1.0E-7D) {
                helper.fail("Loyal trident was not projected out before return: position="
                        + trident.capturedPosition + ", expectedPosition=" + expectedPosition
                        + ", velocity=" + trident.capturedVelocity + ", expectedVelocity=" + expectedVelocity);
                return;
            }
            trident.discard();
            owner.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity", timeoutTicks = 100)
    public static void testTrackedFallingBlockLandsInsideSubLevel(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        helper.runAfterDelay(1, () -> {
            final Vector3d localPosition = new Vector3d(subLevel.getMassTracker().getCenterOfMass())
                    .add(0.0D, 2.5D, 0.0D);
            final Vector3d globalPosition = subLevel.logicalPose().transformPosition(new Vector3d(localPosition));
            final FallingBlockEntity falling = new FallingBlockEntity(EntityType.FALLING_BLOCK, helper.getLevel());
            falling.setPos(globalPosition.x, globalPosition.y, globalPosition.z);
            ((EntityMovementExtension) falling).sable$setTrackingSubLevel(subLevel);
            helper.getLevel().addFreshEntity(falling);
            helper.runAfterDelay(50, () -> {
                final var landed = subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO.above());
                if (!landed.is(Blocks.SAND) || !falling.isRemoved()) {
                    helper.fail("Tracked falling block did not land in plot: landed=" + landed
                            + ", entityRemoved=" + falling.isRemoved() + ", position=" + falling.position());
                    return;
                }
                removeSubLevel(container, subLevel);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testParrotKeepsDancingNearProjectedJukebox(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.JUKEBOX.defaultBlockState());
        helper.runAfterDelay(1, () -> {
            final Vector3d localCenter = new Vector3d(subLevel.getMassTracker().getCenterOfMass());
            final BlockPos plotJukebox = BlockPos.containing(localCenter.x, localCenter.y, localCenter.z);
            final Vector3d globalCenter = subLevel.logicalPose().transformPosition(new Vector3d(localCenter));
            final Parrot parrot = EntityType.PARROT.create(helper.getLevel());
            if (parrot == null) {
                helper.fail("Parrot fixture could not be created");
                return;
            }
            parrot.setPos(globalCenter.x, globalCenter.y + 1.0D, globalCenter.z);
            parrot.setRecordPlayingNearby(plotJukebox, true);
            parrot.aiStep();
            if (!parrot.isPartyParrot()) {
                helper.fail("Parrot stopped dancing beside projected jukebox: parrot=" + parrot.position()
                        + ", plotJukebox=" + plotJukebox + ", globalCenter=" + globalCenter);
                return;
            }
            parrot.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testTamedAnimalTeleportsOntoSubLevelSurface(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnPlatformSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState(), 4);
        helper.runAfterDelay(1, () -> {
            final LivingEntity owner = EntityType.PIG.create(helper.getLevel());
            if (owner == null) {
                helper.fail("Tamed teleport owner could not be created");
                return;
            }
            final Vector3d localOwner = new Vector3d(subLevel.getMassTracker().getCenterOfMass())
                    .add(0.0D, 1.0D, 0.0D);
            final Vector3d globalOwner = subLevel.logicalPose().transformPosition(new Vector3d(localOwner));
            owner.setPos(globalOwner.x, globalOwner.y, globalOwner.z);
            ((EntityMovementExtension) owner).sable$setTrackingSubLevel(subLevel);
            final TestWolf wolf = new TestWolf(helper.getLevel(), owner);
            wolf.setPos(globalOwner.x + 20.0D, globalOwner.y, globalOwner.z + 20.0D);
            // FollowOwnerGoal samples ten random candidate positions per teleport attempt.
            // Fix the validation fixture's seed so this behavioral gate is reproducible.
            wolf.getRandom().setSeed(0L);
            final FollowOwnerGoal goal = new FollowOwnerGoal(wolf, 1.0D, 10.0F, 2.0F, false);
            if (!goal.canUse()) {
                helper.fail("Tamed teleport goal did not start from distant fixture");
                return;
            }
            goal.start();
            goal.tick();
            final double horizontal = Math.max(Math.abs(wolf.getX() - globalOwner.x),
                    Math.abs(wolf.getZ() - globalOwner.z));
            if (horizontal > 3.6D || Math.abs(wolf.getY() - globalOwner.y) > 1.1D) {
                helper.fail("Tamed animal did not teleport onto sublevel surface: owner=" + globalOwner
                        + ", wolf=" + wolf.position() + ", horizontal=" + horizontal);
                return;
            }
            wolf.discard();
            owner.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelGroundPathfinding(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnPlatformSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState(), 5);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final Vector3d localCenter = new Vector3d(subLevel.getMassTracker().getCenterOfMass());
            final Vector3d localStart = new Vector3d(localCenter).add(0.0D, 1.0D, 3.0D);
            final Vector3d localTarget = new Vector3d(localCenter).add(0.0D, 1.0D, -3.0D);
            final Vector3d globalStart = subLevel.logicalPose().transformPosition(new Vector3d(localStart));
            final Vector3d globalTarget = subLevel.logicalPose().transformPosition(new Vector3d(localTarget));
            final Pig walker = EntityType.PIG.create(helper.getLevel());
            final Pig target = EntityType.PIG.create(helper.getLevel());
            if (walker == null || target == null) {
                helper.fail("Rotated pathfinding fixture could not create pigs");
                return;
            }
            walker.setPos(globalStart.x, globalStart.y, globalStart.z);
            target.setPos(globalTarget.x, globalTarget.y, globalTarget.z);
            walker.setNoAi(true);
            target.setNoAi(true);
            walker.setOnGround(true);
            ((EntityMovementExtension) walker).sable$setTrackingSubLevel(subLevel);
            ((EntityMovementExtension) target).sable$setTrackingSubLevel(subLevel);
            helper.getLevel().addFreshEntity(walker);
            helper.getLevel().addFreshEntity(target);

            final Path path = walker.getNavigation().createPath(target, 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) {
                helper.fail("Rotated sub-level pathfinder returned no reachable path: path=" + path
                        + ", localStart=" + localStart + ", localTarget=" + localTarget
                        + ", globalStart=" + globalStart + ", globalTarget=" + globalTarget);
                return;
            }
            path.setNextNodeIndex(path.getNodeCount() - 1);
            final Vec3 projectedEnd = path.getNextEntityPos(walker);
            if (projectedEnd.distanceTo(new Vec3(globalTarget.x, globalTarget.y, globalTarget.z)) > 2.0D) {
                helper.fail("Rotated sub-level path endpoint was not projected into physical space: end="
                        + projectedEnd + ", target=" + globalTarget + ", path=" + path);
                return;
            }
            walker.discard();
            target.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelRailPathfindingContext(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.above(),
                Blocks.RAIL.defaultBlockState(), 2);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final BlockPos plotRail = subLevel.getPlot().getCenterBlock().above();
            final Vector3d physicalRail = subLevel.logicalPose().transformPosition(
                    new Vector3d(plotRail.getX() + 0.5D, plotRail.getY(), plotRail.getZ() + 0.5D));
            final Pig pig = EntityType.PIG.create(helper.getLevel());
            if (pig == null) {
                helper.fail("Rail pathfinding fixture could not create pig");
                return;
            }
            pig.setPos(physicalRail.x, physicalRail.y, physicalRail.z);
            ((EntityMovementExtension) (Object) pig).sable$setTrackingSubLevel(subLevel);

            if (helper.getLevel().getBlockState(pig.blockPosition()).getBlock() instanceof BaseRailBlock) {
                helper.fail("Rail fixture physical position unexpectedly contains a vanilla rail");
                return;
            }
            if (!(helper.getLevel().getBlockState(plotRail).getBlock() instanceof BaseRailBlock)) {
                helper.fail("Rail fixture plot position does not contain its retained rail");
                return;
            }

            final PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(),
                    plotRail.offset(-2, -2, -2), plotRail.offset(2, 2, 2));
            final WalkNodeEvaluator evaluator = new WalkNodeEvaluator();
            evaluator.prepare(region, pig);
            final BlockPathTypes type = evaluator.getBlockPathType(region,
                    plotRail.getX(), plotRail.getY(), plotRail.getZ(), pig);
            evaluator.done();
            if (type != BlockPathTypes.RAIL) {
                helper.fail("Rotated sublevel rail was evaluated against the physical mob position: " + type);
                return;
            }

            pig.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testProjectedEntityQueryInteractionDistanceAndKick(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final Vector3d localCenter = new Vector3d(subLevel.getMassTracker().getCenterOfMass())
                    .add(0.0D, 1.0D, 0.0D);
            final Vector3d globalCenter = subLevel.logicalPose().transformPosition(new Vector3d(localCenter));
            final Minecart retained = EntityType.MINECART.create(helper.getLevel());
            final LivingEntity source = EntityType.PIG.create(helper.getLevel());
            final LivingEntity kicked = EntityType.PIG.create(helper.getLevel());
            if (retained == null || source == null || kicked == null) {
                helper.fail("Projected entity-query fixture could not be created");
                return;
            }
            retained.setPos(localCenter.x, localCenter.y, localCenter.z);
            helper.getLevel().addFreshEntity(retained);
            source.setPos(globalCenter.x - 2.0D, globalCenter.y, globalCenter.z);
            helper.getLevel().addFreshEntity(source);

            final ArrayList<net.minecraft.world.entity.Entity> found = new ArrayList<>();
            final AABB query = new AABB(globalCenter.x - 1.0D, globalCenter.y - 1.0D,
                    globalCenter.z - 1.0D, globalCenter.x + 1.0D, globalCenter.y + 1.0D,
                    globalCenter.z + 1.0D);
            helper.getLevel().getEntities().get(query, found::add);
            if (!found.contains(retained)) {
                helper.fail("Global AABB query did not include retained plot entity: found=" + found);
                return;
            }

            final double expectedDistance = 2.0D;
            final double distance = source.distanceTo(retained);
            final double distanceVec = source.distanceToSqr(retained.position());
            final double distanceComponents = source.distanceToSqr(retained.getX(), retained.getY(), retained.getZ());
            if (Math.abs(distance - expectedDistance) > 1.0E-6D
                    || Math.abs(distanceVec - expectedDistance * expectedDistance) > 1.0E-6D
                    || Math.abs(distanceComponents - expectedDistance * expectedDistance) > 1.0E-6D) {
                helper.fail("Projected entity distance mismatch: distance=" + distance + ", vec=" + distanceVec
                        + ", components=" + distanceComponents);
                return;
            }

            final Vec3 rayStart = new Vec3(globalCenter.x - 3.0D, globalCenter.y + 0.5D, globalCenter.z);
            final Vec3 rayEnd = new Vec3(globalCenter.x + 3.0D, globalCenter.y + 0.5D, globalCenter.z);
            final EntityHitResult hit = ProjectileUtil.getEntityHitResult(source, rayStart, rayEnd,
                    query.inflate(3.0D), entity -> entity == retained, 100.0D);
            if (hit == null || hit.getEntity() != retained) {
                helper.fail("Projected projectile query missed retained entity: hit=" + hit);
                return;
            }

            final Vec3 localVelocity = new Vec3(0.0D, 0.0D, 0.25D);
            kicked.setPos(localCenter.x, localCenter.y + 1.0D, localCenter.z);
            kicked.setDeltaMovement(localVelocity);
            helper.getLevel().addFreshEntity(kicked);
            final Vector3d expectedKickedPosition = subLevel.logicalPose()
                    .transformPosition(new Vector3d(localCenter).add(0.0D, 1.0D, 0.0D));
            final Vector3d expectedKickedVelocity = subLevel.logicalPose()
                    .transformNormal(new Vector3d(localVelocity.x, localVelocity.y, localVelocity.z));
            if (kicked.position().distanceTo(new Vec3(expectedKickedPosition.x, expectedKickedPosition.y,
                    expectedKickedPosition.z)) > 1.0E-7D
                    || kicked.getDeltaMovement().distanceTo(new Vec3(expectedKickedVelocity.x,
                    expectedKickedVelocity.y, expectedKickedVelocity.z)) > 1.0E-7D) {
                helper.fail("Fresh non-retained entity was not kicked out of plot: position=" + kicked.position()
                        + ", expectedPosition=" + expectedKickedPosition + ", velocity="
                        + kicked.getDeltaMovement() + ", expectedVelocity=" + expectedKickedVelocity);
                return;
            }

            kicked.discard();
            source.discard();
            retained.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelBlockDropIsProjectedWithVanillaVelocity(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final BlockPos referencePos = helper.absolutePos(new BlockPos(8, 4, 8));
            final BlockPos plotPos = subLevel.getPlot().getCenterBlock();
            final long seed = 0x5A61E203L;
            helper.getLevel().random.setSeed(seed);
            Block.popResource(helper.getLevel(), referencePos, new ItemStack(Items.DIAMOND));
            final ItemEntity reference = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(referencePos).inflate(2.0D)).stream()
                    .filter(item -> item.getItem().is(Items.DIAMOND)).findFirst().orElse(null);
            if (reference == null) {
                helper.fail("Vanilla block-drop reference entity was not spawned");
                return;
            }

            helper.getLevel().random.setSeed(seed);
            Block.popResource(helper.getLevel(), plotPos, new ItemStack(Items.EMERALD));
            final Vector3d expectedCenter = subLevel.logicalPose().transformPosition(
                    new Vector3d(plotPos.getX() + 0.5D, plotPos.getY() + 0.5D, plotPos.getZ() + 0.5D));
            final ItemEntity projected = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(expectedCenter.x - 2.0D, expectedCenter.y - 2.0D, expectedCenter.z - 2.0D,
                            expectedCenter.x + 2.0D, expectedCenter.y + 2.0D, expectedCenter.z + 2.0D)).stream()
                    .filter(item -> item.getItem().is(Items.EMERALD)).findFirst().orElse(null);
            if (projected == null) {
                helper.fail("Sublevel block drop was not projected into physical space");
                return;
            }

            final Vector3d projectedLocal = subLevel.logicalPose().transformPositionInverse(
                    new Vector3d(projected.getX(), projected.getY(), projected.getZ()));
            final Vec3 expectedLocalOffset = reference.position().subtract(referencePos.getX(),
                    referencePos.getY(), referencePos.getZ());
            final Vec3 actualLocalOffset = new Vec3(projectedLocal.x - plotPos.getX(),
                    projectedLocal.y - plotPos.getY(), projectedLocal.z - plotPos.getZ());
            final double positionError = actualLocalOffset.distanceTo(expectedLocalOffset);
            final double velocityError = projected.getDeltaMovement().distanceTo(reference.getDeltaMovement());
            if (positionError > 1.0E-7D || velocityError > 1.0E-9D) {
                helper.fail("Projected block drop diverged from seeded vanilla reference: localOffset="
                        + actualLocalOffset + ", expectedOffset=" + expectedLocalOffset
                        + ", velocity=" + projected.getDeltaMovement() + ", expectedVelocity="
                        + reference.getDeltaMovement() + ", positionError=" + positionError
                        + ", velocityError=" + velocityError);
                return;
            }

            projected.discard();
            reference.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testEntityInsideProjectedCobwebInvokesBlockBehavior(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.above(),
                Blocks.COBWEB.defaultBlockState(), 3);
        subLevel.updateLastPose();
        helper.runAfterDelay(1, () -> {
            final Vec3 plotBlockCenter = subLevel.getPlot().getCenterBlock().above().getCenter();
            final Vector3d plotCenter = new Vector3d(plotBlockCenter.x, plotBlockCenter.y, plotBlockCenter.z);
            final Vector3d globalCenter = subLevel.logicalPose().transformPosition(new Vector3d(plotCenter));
            final TestPig pig = new TestPig(helper.getLevel());
            pig.setNoGravity(true);
            pig.setPos(globalCenter.x, globalCenter.y, globalCenter.z);
            helper.getLevel().addFreshEntity(pig);
            pig.checkProjectedBlocks();
            final Vec3 expectedSlowdown = new Vec3(0.25D, 0.05D, 0.25D);
            if (pig.insideState == null || !pig.insideState.is(Blocks.COBWEB)
                    || pig.insideSlowdown == null || pig.insideSlowdown.distanceTo(expectedSlowdown) > 1.0E-9D) {
                helper.fail("Projected cobweb did not invoke entityInside behavior: state=" + pig.insideState
                        + ", slowdown=" + pig.insideSlowdown);
                return;
            }

            pig.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testCreatePhysicalCompatibilityInterfaces(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        final var accessor = subLevel.getPlot().getEmbeddedLevelAccessor();
        final BlockState fanState = AllBlocks.ENCASED_FAN.getDefaultState()
                .setValue(EncasedFanBlock.FACING, Direction.EAST);
        accessor.setBlock(BlockPos.ZERO.above(), fanState, 3);
        accessor.setBlock(BlockPos.ZERO.above(2), AllBlocks.FLYWHEEL.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 3);
        final BlockState crushingWheelState = AllBlocks.CRUSHING_WHEEL.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        accessor.setBlock(BlockPos.ZERO.above(3), crushingWheelState, 3);
        container.physicsSystem().getPipeline().teleport(subLevel, subLevel.logicalPose().position(),
                new Quaterniond().rotateY(Math.PI / 2.0D));
        container.physicsSystem().updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final BlockState sailState = AllBlocks.SAIL.getDefaultState()
                    .setValue(BlockStateProperties.FACING, Direction.NORTH);
            if (!(sailState.getBlock() instanceof final BlockSubLevelLiftProvider liftProvider)
                    || !(sailState.getBlock() instanceof final BlockSubLevelCustomCenterOfMass centerProvider)) {
                helper.fail("Create sail did not expose Sable lift/center interfaces");
                return;
            }
            final var center = centerProvider.getCenterOfMass(accessor, sailState);
            if (liftProvider.sable$getNormal(sailState) != Direction.SOUTH
                    || center.distance(new Vector3d(0.5D, 0.5D, 0.5D)) > 1.0E-9D) {
                helper.fail("Create sail physical properties mismatch: normal="
                        + liftProvider.sable$getNormal(sailState) + ", center=" + center);
                return;
            }

            final var fanBlockEntity = accessor.getBlockEntity(BlockPos.ZERO.above());
            if (!(fanBlockEntity instanceof final KineticBlockEntity kinetic)
                    || !(fanBlockEntity instanceof final BlockEntitySubLevelPropellerActor actor)) {
                helper.fail("Create encased fan did not expose Sable propeller actor: " + fanBlockEntity);
                return;
            }
            kinetic.setSpeed(60.0F);
            actor.sable$tick(subLevel);
            final BlockEntityPropeller propeller = actor.getPropeller();
            if (!propeller.isActive() || propeller.getBlockDirection() != Direction.EAST
                    || Math.abs(propeller.getAirflow()) < 1.0E-6D
                    || Math.abs(propeller.getThrust() - 3.0D * propeller.getAirflow()) > 1.0E-6D) {
                helper.fail("Create fan physical properties mismatch: active=" + propeller.isActive()
                        + ", direction=" + propeller.getBlockDirection() + ", airflow="
                        + propeller.getAirflow() + ", thrust=" + propeller.getThrust());
                return;
            }

            final BlockPos fanPlotPos = subLevel.getPlot().getCenterBlock().above();
            final Vector3d localAirTarget = new Vector3d(fanPlotPos.getX() + 2.5D,
                    fanPlotPos.getY() + 0.5D, fanPlotPos.getZ() + 0.5D);
            final Vector3d physicalAirTarget = subLevel.logicalPose().transformPosition(new Vector3d(localAirTarget));
            final Pig airflowPig = EntityType.PIG.create(helper.getLevel());
            if (!(fanBlockEntity instanceof final EncasedFanBlockEntity fan) || airflowPig == null) {
                helper.fail("Create airflow fixture could not be created");
                return;
            }
            airflowPig.setNoGravity(true);
            airflowPig.setPos(physicalAirTarget.x, physicalAirTarget.y, physicalAirTarget.z);
            airflowPig.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(airflowPig);
            kinetic.setSpeed(128.0F);
            fan.airCurrent.rebuild();
            fan.airCurrent.findEntities();
            fan.airCurrent.tick();
            final Vec3 physicalAirMotion = airflowPig.getDeltaMovement();
            final Vector3d localAirMotion = subLevel.logicalPose().transformNormalInverse(
                    new Vector3d(physicalAirMotion.x, physicalAirMotion.y, physicalAirMotion.z));
            if (localAirMotion.x <= 0.0D || Math.abs(localAirMotion.y) > 1.0E-6D
                    || Math.abs(localAirMotion.z) > 1.0E-6D) {
                helper.fail("Create airflow was not projected along the fan's local +X axis: local="
                        + localAirMotion + ", physical=" + physicalAirMotion);
                return;
            }
            airflowPig.discard();

            if (!(AllBlocks.BELT.get() instanceof BlockWithSubLevelCollisionCallback)
                    || !(AllBlocks.HAUNTED_BELL.get() instanceof BlockWithSubLevelCollisionCallback)
                    || !(AllBlocks.CHAIN_CONVEYOR.get() instanceof BlockSubLevelAssemblyListener)
                    || !(AllBlocks.DISPLAY_LINK.get() instanceof BlockSubLevelAssemblyListener)) {
                helper.fail("Create collision/assembly compatibility interfaces were not applied");
                return;
            }

            final var flywheelBlockEntity = accessor.getBlockEntity(BlockPos.ZERO.above(2));
            if (!(flywheelBlockEntity instanceof final KineticBlockEntity flywheelKinetic)
                    || !(flywheelBlockEntity instanceof final BlockEntitySubLevelReactionWheel reactionWheel)) {
                helper.fail("Create flywheel did not expose Sable reaction-wheel behavior: " + flywheelBlockEntity);
                return;
            }
            for (int tick = 0; tick < 32; tick++) {
                // The isolated fixture has no Create kinetic network, so keep
                // its input at the same stable 60 RPM that a powered network supplies.
                flywheelKinetic.setSpeed(60.0F);
                flywheelKinetic.tick();
            }
            final Vector3d angularVelocity = new Vector3d();
            reactionWheel.sable$getAngularVelocity(angularVelocity);
            final double expectedAngularSpeed = -(1.0D - Math.pow(31.0D / 32.0D, 32.0D)) * Math.PI * 2.0D;
            if (Math.abs(angularVelocity.x - expectedAngularSpeed) > 1.0E-5D
                    || Math.abs(angularVelocity.y) > 1.0E-9D || Math.abs(angularVelocity.z) > 1.0E-9D) {
                helper.fail("Create flywheel angular velocity mismatch: expected X="
                        + expectedAngularSpeed + ", actual=" + angularVelocity);
                return;
            }
            final var savedFlywheel = flywheelBlockEntity.saveWithoutMetadata();
            if (!savedFlywheel.contains("SmoothedSpeed")) {
                helper.fail("Create flywheel did not persist the Sable smoothed speed");
                return;
            }

            final BlockPos wheelPlotPos = subLevel.getPlot().getCenterBlock().above(3);
            final var crushingBlockEntity = accessor.getBlockEntity(BlockPos.ZERO.above(3));
            if (!(crushingBlockEntity instanceof final KineticBlockEntity crushingKinetic)) {
                helper.fail("Create crushing-wheel block entity fixture was not created");
                return;
            }
            crushingKinetic.setSpeed(20.0F);
            final Vector3d localEntity = new Vector3d(wheelPlotPos.getX() + 0.5D,
                    wheelPlotPos.getY() + 1.5D, wheelPlotPos.getZ() + 0.5D);
            final Vector3d physicalEntity = subLevel.logicalPose().transformPosition(new Vector3d(localEntity));
            final Pig crushingPig = EntityType.PIG.create(helper.getLevel());
            if (crushingPig == null) {
                helper.fail("Create crushing-wheel entity fixture could not be created");
                return;
            }
            crushingPig.setPos(physicalEntity.x, physicalEntity.y, physicalEntity.z);
            crushingPig.setOnGround(true);
            crushingPig.setDeltaMovement(Vec3.ZERO);
            crushingWheelState.getBlock().entityInside(crushingWheelState, helper.getLevel(),
                    wheelPlotPos, crushingPig);
            final Vec3 actualMovement = crushingPig.getDeltaMovement();
            final Vector3d localMovement = subLevel.logicalPose().transformNormalInverse(
                    new Vector3d(actualMovement.x, actualMovement.y, actualMovement.z));
            // At 20 RPM the source applies exactly +1 on local Z. Its local-X
            // term is Create's bounded 0.1 centering correction within the block.
            if (Math.abs(localMovement.z - 1.0D) > 1.0E-6D
                    || Math.abs(localMovement.y) > 1.0E-9D || Math.abs(localMovement.x) > 0.100001D) {
                helper.fail("Create crushing-wheel impulse was not projected from its documented local axes: local="
                        + localMovement + ", physical=" + actualMovement);
                return;
            }
            crushingPig.discard();

            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    /**
     * Proves that plot block entities are advanced by the real ServerLevel ticker and that
     * Create can build a kinetic network there.  Calling KineticBlockEntity.tick() directly
     * is deliberately forbidden in this fixture because that previously hid a dead runtime
     * ticker/network path.
     */
    @GameTest(template = "physicstest.gravity", timeoutTicks = 300)
    public static void testCreateKineticNetworkTicksInsideSubLevel(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final BlockState motorState = AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), motorState);
        final var accessor = subLevel.getPlot().getEmbeddedLevelAccessor();
        accessor.setBlock(BlockPos.ZERO.east(), AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 3);

        // Keep the real server alive with the remote plot loaded for long enough to
        // exercise ServerChunkCache.tickChunks repeatedly.  A former broad override of
        // ServerLevel.shouldTickBlocksAt crashed here by admitting plot storage chunks
        // into weather/random ticks and probing an intentionally unallocated neighbour.
        helper.runAfterDelay(220, () -> {
            final var motor = accessor.getBlockEntity(BlockPos.ZERO);
            final var shaft = accessor.getBlockEntity(BlockPos.ZERO.east());
            if (!(motor instanceof final KineticBlockEntity motorKinetic)
                    || !(shaft instanceof final KineticBlockEntity shaftKinetic)) {
                removeSubLevel(container, subLevel);
                helper.fail("Create kinetic ticker fixture lost its motor or shaft block entity: motor="
                        + motor + ", shaft=" + shaft);
                return;
            }
            if (Math.abs(motorKinetic.getSpeed()) < 0.01F
                    || Math.abs(shaftKinetic.getSpeed()) < 0.01F
                    || !shaftKinetic.hasSource()) {
                final BlockPos globalMotorPos = motor.getBlockPos();
                final var motorChunk = helper.getLevel().getChunkAt(globalMotorPos);
                final var tickerAccess = (LevelBlockEntityTickerValidationAccessor) (Object) helper.getLevel();
                final long activeTickerCount = tickerAccess.sable$getBlockEntityTickers().stream()
                        .filter(ticker -> ticker.getPos().equals(globalMotorPos)
                                || ticker.getPos().equals(shaft.getBlockPos()))
                        .count();
                final long pendingTickerCount = tickerAccess.sable$getPendingBlockEntityTickers().stream()
                        .filter(ticker -> ticker.getPos().equals(globalMotorPos)
                                || ticker.getPos().equals(shaft.getBlockPos()))
                        .count();
                removeSubLevel(container, subLevel);
                helper.fail("Create sublevel kinetic network did not advance autonomously: motorSpeed="
                        + motorKinetic.getSpeed() + ", shaftSpeed=" + shaftKinetic.getSpeed()
                        + ", shaftHasSource=" + shaftKinetic.hasSource()
                        + ", chunkFullStatus=" + motorChunk.getFullStatus()
                        + ", entitiesLoaded=" + helper.getLevel().areEntitiesLoaded(
                        new ChunkPos(globalMotorPos).toLong())
                        + ", withinWorldBorder=" + helper.getLevel().getWorldBorder().isWithinBounds(globalMotorPos)
                        + ", activeTickers=" + activeTickerCount
                        + ", pendingTickers=" + pendingTickerCount
                        + ", motorNeedsSpeedUpdate=" + motorKinetic.needsSpeedUpdate()
                        + ", shaftNeedsSpeedUpdate=" + shaftKinetic.needsSpeedUpdate());
                return;
            }
            if (helper.getLevel().shouldTickBlocksAt(motor.getBlockPos())) {
                removeSubLevel(container, subLevel);
                helper.fail("Plot block-entity tick policy leaked into the 1.20.1 whole-chunk/random-tick gate");
                return;
            }

            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    /**
     * Exercises the vanilla Bell lifecycle that originally exposed the split
     * between admitted plot block-entity ticks and deferred plot block events.
     */
    @GameTest(template = "physicstest.gravity", timeoutTicks = 80)
    public static void testBellBlockEventRunsInsideSubLevel(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer container = requireContainer(level);
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        final BlockPos localBellPos = BlockPos.ZERO.above();
        final BlockPos plotBellPos = subLevel.getPlot().getCenterBlock().above();
        subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(
                localBellPos, Blocks.BELL.defaultBlockState(), 3);

        if (!(level.getBlockEntity(plotBellPos) instanceof BellBlockEntity)) {
            removeSubLevel(container, subLevel);
            helper.fail("Plot bell fixture did not create its block entity");
            return;
        }
        if (level.shouldTickBlocksAt(plotBellPos)) {
            removeSubLevel(container, subLevel);
            helper.fail("Plot bell fixture leaked into the whole-chunk/random-tick gate");
            return;
        }
        if (!((BellBlock) Blocks.BELL).attemptToRing(level, plotBellPos, Direction.NORTH)) {
            removeSubLevel(container, subLevel);
            helper.fail("Vanilla BellBlock refused the plot bell fixture");
            return;
        }

        helper.runAfterDelay(12, () -> {
            final var blockEntity = level.getBlockEntity(plotBellPos);
            if (!(blockEntity instanceof final BellBlockEntity bell)) {
                removeSubLevel(container, subLevel);
                helper.fail("Plot bell block entity disappeared before its event was processed");
                return;
            }
            final List<LivingEntity> nearbyEntities =
                    ((BellBlockEntityValidationAccessor) (Object) bell).sable$getNearbyEntities();
            if (nearbyEntities == null) {
                removeSubLevel(container, subLevel);
                helper.fail("Plot bell ticked without receiving the block event that initializes nearby entities");
                return;
            }
            if (level.shouldTickBlocksAt(plotBellPos)) {
                removeSubLevel(container, subLevel);
                helper.fail("Plot block-event admission widened the whole-chunk/random-tick gate");
                return;
            }

            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    /**
     * Covers the player's real workflow: Create block entities are first created in the
     * overworld and then moved through SubLevelAssemblyHelper.  The assembled network must
     * be registered with the vanilla ServerLevel ticker and rebuild itself without a direct
     * tick() call or a test-authored speed value.
     */
    @GameTest(template = "physicstest.gravity", timeoutTicks = 140)
    public static void testAssembledCreateKineticNetworkTicksInsideSubLevel(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer container = requireContainer(level);
        final BlockPos motorSource = helper.absolutePos(new BlockPos(2, 9, 2));
        final BlockPos shaftSource = motorSource.east();
        final BlockState motorState = AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        final BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);

        level.setBlock(motorSource, motorState, 3);
        level.setBlock(shaftSource, shaftState, 3);
        if (!(level.getBlockEntity(motorSource) instanceof KineticBlockEntity)
                || !(level.getBlockEntity(shaftSource) instanceof KineticBlockEntity)) {
            helper.fail("Create assembly ticker fixture could not create its source block entities");
            return;
        }

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                level,
                motorSource,
                List.of(motorSource, shaftSource),
                new BoundingBox3i(motorSource.getX(), motorSource.getY(), motorSource.getZ(),
                        shaftSource.getX(), shaftSource.getY(), shaftSource.getZ()));
        final BlockPos plotMotor = subLevel.getPlot().getCenterBlock();
        final BlockPos plotShaft = plotMotor.east();

        if (!level.getBlockState(motorSource).isAir() || !level.getBlockState(shaftSource).isAir()) {
            removeSubLevel(container, subLevel);
            helper.fail("Create assembly ticker fixture duplicated source blocks instead of moving them");
            return;
        }

        helper.runAfterDelay(50, () -> {
            final var motor = level.getBlockEntity(plotMotor);
            final var shaft = level.getBlockEntity(plotShaft);
            if (!(motor instanceof final KineticBlockEntity motorKinetic)
                    || !(shaft instanceof final KineticBlockEntity shaftKinetic)) {
                removeSubLevel(container, subLevel);
                helper.fail("Assembled Create kinetic fixture lost its motor or shaft block entity: motor="
                        + motor + ", shaft=" + shaft);
                return;
            }
            if (Math.abs(motorKinetic.getSpeed()) < 0.01F
                    || Math.abs(shaftKinetic.getSpeed()) < 0.01F
                    || !shaftKinetic.hasSource()) {
                final var tickerAccess = (LevelBlockEntityTickerValidationAccessor) (Object) level;
                final long activeTickerCount = tickerAccess.sable$getBlockEntityTickers().stream()
                        .filter(ticker -> ticker.getPos().equals(plotMotor)
                                || ticker.getPos().equals(plotShaft))
                        .count();
                removeSubLevel(container, subLevel);
                helper.fail("Assembled Create kinetic network did not advance autonomously: motorSpeed="
                        + motorKinetic.getSpeed() + ", shaftSpeed=" + shaftKinetic.getSpeed()
                        + ", shaftHasSource=" + shaftKinetic.hasSource()
                        + ", activeTickers=" + activeTickerCount
                        + ", motorNeedsSpeedUpdate=" + motorKinetic.needsSpeedUpdate()
                        + ", shaftNeedsSpeedUpdate=" + shaftKinetic.needsSpeedUpdate());
                return;
            }
            if (level.shouldTickBlocksAt(plotMotor)) {
                removeSubLevel(container, subLevel);
                helper.fail("Assembled plot was admitted into the 1.20.1 whole-chunk/random-tick gate");
                return;
            }

            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testForgePlayerReachAcrossSubLevel(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        final BlockPos plotTarget = subLevel.getPlot().getCenterBlock();
        final Vector3d physicalTarget = subLevel.logicalPose().transformPosition(
                new Vector3d(plotTarget.getX() + 0.5D, plotTarget.getY() + 0.5D, plotTarget.getZ() + 0.5D));

        helper.runAfterDelay(1, () -> {
            final var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.setPos(physicalTarget.x, physicalTarget.y - player.getEyeHeight(), physicalTarget.z - 2.0D);
            final Pig plotEntity = EntityType.PIG.create(helper.getLevel());
            if (plotEntity == null) {
                helper.fail("Player reach entity fixture could not be created");
                return;
            }
            plotEntity.setPos(plotTarget.getX() + 0.5D, plotTarget.getY(), plotTarget.getZ() + 0.5D);
            if (!player.canReach(plotTarget, 1.5D)) {
                helper.fail("Forge block reach did not project the player eye into the sublevel");
                return;
            }
            if (!player.canReach(plotEntity, 3.0D)) {
                helper.fail("Forge entity reach did not project the player eye into the sublevel");
                return;
            }
            final BlockPos remoteVanilla = plotTarget.offset(64, 0, 0);
            if (player.canReach(remoteVanilla, 1.5D)) {
                helper.fail("Forge block reach fallback incorrectly accepted a remote vanilla block");
                return;
            }

            plotEntity.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testRotatedSubLevelFluidStateAndPushing(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 9.0D, 2.5D)), Blocks.STONE.defaultBlockState());
        final var embedded = subLevel.getPlot().getEmbeddedLevelAccessor();
        final BlockPos lowerWater = BlockPos.ZERO.above();
        final BlockPos upperWater = lowerWater.above();
        embedded.setBlock(lowerWater, Blocks.WATER.defaultBlockState(), 2);
        embedded.setBlock(upperWater, Blocks.WATER.defaultBlockState(), 2);
        embedded.setBlock(lowerWater.east(),
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 2);
        embedded.setBlock(lowerWater.west(), Blocks.STONE.defaultBlockState(), 2);
        embedded.setBlock(lowerWater.north(), Blocks.STONE.defaultBlockState(), 2);
        embedded.setBlock(lowerWater.south(), Blocks.STONE.defaultBlockState(), 2);

        final Vec3 localFlow = embedded.getFluidState(lowerWater).getFlow(embedded, lowerWater);
        if (localFlow.horizontalDistanceSqr() <= 1.0E-8D) {
            removeSubLevel(container, subLevel);
            helper.fail("Fluid fixture did not produce a measurable local flow: " + localFlow);
            return;
        }

        final Quaterniond quarterTurn = new Quaterniond().rotateY(Math.PI / 2.0D);
        physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), quarterTurn);
        physicsSystem.updatePose(subLevel);
        subLevel.updateLastPose();

        helper.runAfterDelay(1, () -> {
            final BlockPos plotWater = subLevel.getPlot().getCenterBlock().above();
            final Vector3d physicalBody = subLevel.logicalPose().transformPosition(new Vector3d(
                    plotWater.getX() + 0.5D, plotWater.getY(), plotWater.getZ() + 0.5D));
            final TestPig pig = new TestPig(helper.getLevel());
            pig.setNoGravity(true);
            pig.setPos(physicalBody.x, physicalBody.y, physicalBody.z);
            pig.setDeltaMovement(Vec3.ZERO);

            pig.updateFluidHeightAndDoFluidPushing(state -> true);
            final IForgeEntity forgePig = pig;
            final double waterHeight = forgePig.getFluidTypeHeight(ForgeMod.WATER_TYPE.get());
            if (!(waterHeight > 0.0D)) {
                helper.fail("Rotated sublevel water did not update Forge fluid height: " + waterHeight);
                return;
            }

            final Vec3 expectedFlow = subLevel.logicalPose().transformNormal(localFlow).normalize();
            final Vec3 actualPush = pig.getDeltaMovement();
            if (actualPush.horizontalDistanceSqr() <= 1.0E-8D
                    || actualPush.normalize().dot(expectedFlow) < 0.99D) {
                helper.fail("Sublevel fluid push was not transformed by the sublevel pose: local="
                        + localFlow + ", expected=" + expectedFlow + ", actual=" + actualPush);
                return;
            }

            pig.baseTick();
            if (forgePig.getEyeInFluidType() != ForgeMod.WATER_TYPE.get()) {
                helper.fail("Rotated sublevel water did not update the Forge eye fluid type: "
                        + forgePig.getEyeInFluidType());
                return;
            }
            if (!forgePig.canStartSwimming()) {
                helper.fail("Forge swimming predicate rejected matching body and eye water in a sublevel");
                return;
            }
            if (Sable.HELPER.getTrackingSubLevel(pig) != subLevel) {
                helper.fail("Entity fluid intersection did not retain the intersected sublevel");
                return;
            }

            pig.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testServerClipContextCarriesSableState(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 7.0D, 2.5D)),
                Blocks.STONE.defaultBlockState());

        helper.runAfterDelay(1, () -> {
            final Vec3 from = Vec3.atCenterOf(subLevel.getPlot().getCenterBlock());
            final Vec3 to = from.add(0.0D, 2.0D, 0.0D);
            final ClipContext context = new ClipContext(from, to,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, null);
            if (!(context instanceof ClipContextExtension)) {
                removeSubLevel(container, subLevel);
                helper.fail("Server ClipContext is missing Sable's extension Mixin");
                return;
            }
            helper.getLevel().clip(context);
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "physicstest.gravity")
    public static void testSubLevelLandingUsesLocalBlockSound(final GameTestHelper helper) {
        final ServerSubLevelContainer container = requireContainer(helper.getLevel());
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container,
                absolutePosition(helper, new Vector3d(2.5D, 7.0D, 2.5D)),
                Blocks.GOLD_BLOCK.defaultBlockState());

        helper.runAfterDelay(1, () -> {
            final BlockPos plotBlock = subLevel.getPlot().getCenterBlock();
            final Vector3d physicalFeet = subLevel.logicalPose().transformPosition(new Vector3d(
                    plotBlock.getX() + 0.5D, plotBlock.getY() + 1.0D, plotBlock.getZ() + 0.5D));
            final BlockPos physicalSupport = BlockPos.containing(
                    physicalFeet.x, physicalFeet.y - 0.2D, physicalFeet.z);
            helper.getLevel().setBlock(physicalSupport, Blocks.STONE.defaultBlockState(), 3);

            final TestPig pig = new TestPig(helper.getLevel());
            pig.setPos(physicalFeet.x, physicalFeet.y, physicalFeet.z);
            ((EntityMovementExtension) (Object) pig).sable$setTrackingSubLevel(subLevel);
            pig.playCapturedBlockFallSound();

            final SoundEvent expected = SoundType.METAL.getFallSound();
            final SoundEvent wrongPhysicalSound = SoundType.STONE.getFallSound();
            if (pig.capturedSound != expected || pig.capturedSound == wrongPhysicalSound) {
                removeSubLevel(container, subLevel);
                helper.fail("Landing sound did not use the tracked sublevel support block: expected="
                        + expected + ", physicalFallback=" + wrongPhysicalSound
                        + ", actual=" + pig.capturedSound + ", plotBlock=" + plotBlock
                        + ", physicalSupport=" + physicalSupport);
                return;
            }

            pig.discard();
            removeSubLevel(container, subLevel);
            helper.succeed();
        });
    }

    private static ServerSubLevel spawnSingleBlockSubLevel(final ServerSubLevelContainer container,
                                                            final Vector3d position,
                                                            final net.minecraft.world.level.block.state.BlockState state) {
        final Pose3d pose = new Pose3d();
        pose.position().set(position);
        final SubLevel subLevel = container.allocateNewSubLevel(pose);
        final LevelPlot plot = subLevel.getPlot();
        final ChunkPos center = plot.getCenterChunk();
        plot.newEmptyChunk(center);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, state, 3);
        subLevel.updateLastPose();
        return (ServerSubLevel) subLevel;
    }

    private static ServerSubLevel spawnPlatformSubLevel(final ServerSubLevelContainer container,
                                                         final Vector3d position,
                                                         final net.minecraft.world.level.block.state.BlockState state,
                                                         final int radius) {
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(container, position, state);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(x, 0, z), state, 3);
            }
        }
        return subLevel;
    }

    private static Vector3d absolutePosition(final GameTestHelper helper, final Vector3d localPosition) {
        final Vec3 result = helper.absoluteVec(new Vec3(localPosition.x, localPosition.y, localPosition.z));
        return new Vector3d(result.x, result.y, result.z);
    }

    private static void removeSubLevel(final ServerSubLevelContainer container,
                                       final ServerSubLevel subLevel) {
        final LevelPlot plot = subLevel.getPlot();
        final Vector2i origin = container.getOrigin();
        container.removeSubLevel(plot.plotPos.x - origin.x, plot.plotPos.z - origin.y,
                SubLevelRemovalReason.REMOVED);
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    private static final class TestProjectileDispenseBehavior extends AbstractProjectileDispenseBehavior {
        private Projectile projectile;

        @Override
        protected Projectile getProjectile(final Level level, final Position position, final ItemStack stack) {
            this.projectile = new Snowball(level, position.x(), position.y(), position.z());
            return this.projectile;
        }
    }

    private static final class TestArrow extends AbstractArrow {
        private TestArrow(final Level level) {
            super(EntityType.ARROW, level);
        }

        private void hit(final BlockHitResult result) {
            this.onHitBlock(result);
        }

        @Override
        protected ItemStack getPickupItem() {
            return new ItemStack(Items.ARROW);
        }
    }

    private static class TestTrident extends ThrownTrident {
        private boolean capture;
        private Vec3 capturedPosition;
        private Vec3 capturedVelocity;

        private TestTrident(final Level level, final LivingEntity owner, final ItemStack stack) {
            super(level, owner, stack);
        }

        private void armCapture() {
            this.capture = true;
        }

        @Override
        public void setNoPhysics(final boolean noPhysics) {
            if (this.capture) {
                this.capturedPosition = this.position();
                this.capturedVelocity = this.getDeltaMovement();
                this.capture = false;
            }
            super.setNoPhysics(noPhysics);
        }
    }

    private static final class TestWolf extends Wolf {
        private final LivingEntity testOwner;

        private TestWolf(final Level level, final LivingEntity owner) {
            super(EntityType.WOLF, level);
            this.testOwner = owner;
        }

        @Override
        public LivingEntity getOwner() {
            return this.testOwner;
        }
    }

    private static final class TestPig extends Pig {
        private BlockState insideState;
        private Vec3 insideSlowdown;
        private SoundEvent capturedSound;

        private TestPig(final Level level) {
            super(EntityType.PIG, level);
        }

        private void checkProjectedBlocks() {
            this.checkInsideBlocks();
        }

        private void playCapturedBlockFallSound() {
            this.capturedSound = null;
            this.playBlockFallSound();
        }

        @Override
        public void playSound(final SoundEvent sound, final float volume, final float pitch) {
            this.capturedSound = sound;
        }

        @Override
        public void makeStuckInBlock(final BlockState state, final Vec3 slowdown) {
            this.insideState = state;
            this.insideSlowdown = slowdown;
            super.makeStuckInBlock(state, slowdown);
        }
    }
}
