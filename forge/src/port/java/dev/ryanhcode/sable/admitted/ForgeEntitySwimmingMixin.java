package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.admitted.ForgeFluidInterimCalculation;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.extensions.IForgeEntity;
import net.minecraftforge.fluids.FluidType;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/** Forge 1.20.1 entry-point adaptation of upstream entity_swimming.EntityMixin. */
@Mixin(value = Entity.class, priority = 500)
public abstract class ForgeEntitySwimmingMixin {
    @Shadow public abstract boolean touchingUnloadedChunk();
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract Vec3 getDeltaMovement();
    @Shadow public abstract void setDeltaMovement(Vec3 movement);
    @Shadow(remap = false) protected abstract void setFluidTypeHeight(FluidType type, double height);
    @Shadow public abstract BlockPos blockPosition();
    @Shadow public abstract Level level();
    @Shadow public abstract Vec3 getEyePosition();
    @Shadow private Level level;
    @Shadow private Vec3 position;
    @Shadow(remap = false) private FluidType forgeFluidTypeOnEyes;

    /**
     * @author RyanH; Forge 1.20.1 adaptation by the Sable port
     * @reason Apply vanilla and sublevel fluid height/pushing through Forge's predicate entry point.
     */
    @Overwrite(remap = false)
    public void updateFluidHeightAndDoFluidPushing(final Predicate<FluidState> shouldUpdate) {
        if (this.touchingUnloadedChunk()) {
            return;
        }

        final Entity self = (Entity) (Object) this;
        final IForgeEntity forgeSelf = (IForgeEntity) self;
        final AABB bounds = this.getBoundingBox().deflate(0.001D);
        Object2ObjectMap<FluidType, ForgeFluidInterimCalculation> calculations = null;
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = Mth.floor(bounds.minX); x < Mth.ceil(bounds.maxX); x++) {
            for (int y = Mth.floor(bounds.minY); y < Mth.ceil(bounds.maxY); y++) {
                for (int z = Mth.floor(bounds.minZ); z < Mth.ceil(bounds.maxZ); z++) {
                    mutablePos.set(x, y, z);
                    final FluidState state = this.level.getFluidState(mutablePos);
                    final FluidType type = state.getFluidType();
                    if (type.isAir() || !shouldUpdate.test(state)) {
                        continue;
                    }
                    final double surface = y + state.getHeight(this.level, mutablePos);
                    if (surface < bounds.minY) {
                        continue;
                    }
                    if (calculations == null) {
                        calculations = new Object2ObjectArrayMap<>();
                    }
                    final ForgeFluidInterimCalculation calculation = calculations.computeIfAbsent(type,
                            ignored -> new ForgeFluidInterimCalculation());
                    calculation.fluidHeight = Math.max(surface - bounds.minY, calculation.fluidHeight);
                    sable$accumulateFlow(forgeSelf, calculation, state, type, mutablePos, null);
                }
            }
        }

        final ActiveSableCompanion companion = Sable.HELPER;
        final BoundingBox3d globalBounds = new BoundingBox3d(bounds);
        final BoundingBox3d localBounds = new BoundingBox3d();
        final Vector3d entityCenter = new Vector3d();
        final Vector3d entitySize = new Vector3d();
        final Quaterniond entityOrientation = new Quaterniond();

        for (final SubLevel subLevel : companion.getAllIntersecting(this.level, globalBounds)) {
            final Pose3dc pose = subLevel.lastPose();
            globalBounds.transformInverse(pose, localBounds);
            final LevelReusedVectors sink = ((LevelExtension) this.level).sable$getJOMLSink();
            final Quaterniond localEntityOrientation = pose.orientation().conjugate(entityOrientation);
            localEntityOrientation.rotateY(SubLevelEntityCollision.getHitBoxYaw(pose));
            final OrientedBoundingBox3d entityBox = new OrientedBoundingBox3d(
                    pose.transformPositionInverse(globalBounds.center(entityCenter)),
                    globalBounds.size(entitySize), localEntityOrientation, sink);
            final OrientedBoundingBox3d fluidBox = new OrientedBoundingBox3d(
                    new Vector3d(), new Vector3d(1.0D), JOMLConversion.QUAT_IDENTITY, sink);
            double minimumEntityY = Double.MAX_VALUE;
            boolean minimumComputed = false;

            for (int x = Mth.floor(localBounds.minX); x < Mth.ceil(localBounds.maxX); x++) {
                for (int y = Mth.floor(localBounds.minY); y < Mth.ceil(localBounds.maxY); y++) {
                    for (int z = Mth.floor(localBounds.minZ); z < Mth.ceil(localBounds.maxZ); z++) {
                        mutablePos.set(x, y, z);
                        final FluidState state = this.level.getFluidState(mutablePos);
                        final FluidType type = state.getFluidType();
                        if (type.isAir() || !shouldUpdate.test(state)) {
                            continue;
                        }
                        final double surface = y + state.getHeight(this.level, mutablePos);
                        if (!minimumComputed) {
                            for (final Vector3d vertex : entityBox.vertices(sink.a)) {
                                minimumEntityY = Math.min(minimumEntityY, vertex.y);
                            }
                            minimumComputed = true;
                        }
                        if (surface < minimumEntityY) {
                            continue;
                        }
                        fluidBox.getPosition().set(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (!(OrientedBoundingBox3d.sat(entityBox, fluidBox).lengthSquared() > 0.0D)) {
                            continue;
                        }
                        if (calculations == null) {
                            calculations = new Object2ObjectArrayMap<>();
                        }
                        final ForgeFluidInterimCalculation calculation = calculations.computeIfAbsent(type,
                                ignored -> new ForgeFluidInterimCalculation());
                        calculation.fluidHeight = Math.max(surface - minimumEntityY, calculation.fluidHeight);
                        if (companion.getTrackingSubLevel(self) == null && companion.getContaining(self) != subLevel) {
                            ((EntityMovementExtension) self).sable$setTrackingSubLevel(subLevel);
                        }
                        sable$accumulateFlow(forgeSelf, calculation, state, type, mutablePos, pose);
                    }
                }
            }
        }

        if (calculations != null) {
            calculations.forEach((type, calculation) -> sable$applyFluidCalculation(forgeSelf, type, calculation));
        }
    }

    @Unique
    private void sable$accumulateFlow(final IForgeEntity forgeSelf,
                                      final ForgeFluidInterimCalculation calculation,
                                      final FluidState state,
                                      final FluidType type,
                                      final BlockPos pos,
                                      final Pose3dc pose) {
        if (!forgeSelf.isPushedByFluid(type)) {
            return;
        }
        Vec3 flow = state.getFlow(this.level, pos);
        if (calculation.fluidHeight < 0.4D) {
            flow = flow.scale(calculation.fluidHeight);
        }
        if (pose != null) {
            flow = pose.transformNormal(flow);
        }
        calculation.flowVector = calculation.flowVector.add(flow);
        calculation.blockCount++;
    }

    @Unique
    private void sable$applyFluidCalculation(final IForgeEntity forgeSelf,
                                             final FluidType type,
                                             final ForgeFluidInterimCalculation calculation) {
        Vec3 flow = calculation.flowVector;
        if (flow.length() > 0.0D) {
            if (calculation.blockCount > 0) {
                flow = flow.scale(1.0D / calculation.blockCount);
            }
            if (!((Object) this instanceof Player)) {
                flow = flow.normalize();
            }
            final Vec3 existing = this.getDeltaMovement();
            flow = flow.scale(forgeSelf.getFluidMotionScale(type));
            if (Math.abs(existing.x) < 0.003D && Math.abs(existing.z) < 0.003D
                    && flow.length() < 0.0045000000000000005D) {
                flow = flow.normalize().scale(0.0045000000000000005D);
            }
            this.setDeltaMovement(existing.add(flow));
        }
        this.setFluidTypeHeight(type, calculation.fluidHeight);
    }

    public boolean canStartSwimming() {
        final IForgeEntity forgeSelf = (IForgeEntity) (Object) this;
        FluidType bodyFluid = this.level().getFluidState(this.blockPosition()).getFluidType();
        if (bodyFluid.isAir()) {
            bodyFluid = forgeSelf.getMaxHeightFluidType();
        }
        if (bodyFluid == Fluids.EMPTY.getFluidType()) {
            final BoundingBox3d searchBounds = new BoundingBox3d(this.blockPosition()).expand(0.5D);
            for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(this.level, searchBounds)) {
                final BlockPos localPos = BlockPos.containing(
                        subLevel.lastPose().transformPositionInverse(this.position));
                bodyFluid = this.level.getFluidState(localPos).getFluidType();
                if (bodyFluid != Fluids.EMPTY.getFluidType()) {
                    break;
                }
            }
        }
        final FluidType eyeFluid = forgeSelf.getEyeInFluidType();
        return !eyeFluid.isAir() && forgeSelf.canSwimInFluidType(eyeFluid)
                && forgeSelf.canSwimInFluidType(bodyFluid);
    }

    @Inject(method = "updateFluidOnEyes", at = @At("TAIL"))
    private void sable$subLevelFluidOnEyes(final CallbackInfo callback) {
        if (this.forgeFluidTypeOnEyes != ForgeMod.EMPTY_TYPE.get()
                && this.forgeFluidTypeOnEyes != Fluids.EMPTY.getFluidType()) {
            return;
        }
        final Vec3 globalEye = this.getEyePosition();
        final BoundingBox3d searchBounds = new BoundingBox3d(BlockPos.containing(globalEye)).expand(0.5D);
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(this.level, searchBounds)) {
            final Vec3 localEye = subLevel.lastPose().transformPositionInverse(globalEye);
            final BlockPos localPos = BlockPos.containing(localEye);
            final FluidState state = this.level.getFluidState(localPos);
            final double surface = localPos.getY() + state.getHeight(this.level, localPos);
            if (surface > localEye.y) {
                this.forgeFluidTypeOnEyes = state.getFluidType();
                if (this.forgeFluidTypeOnEyes != ForgeMod.EMPTY_TYPE.get()
                        && this.forgeFluidTypeOnEyes != Fluids.EMPTY.getFluidType()) {
                    return;
                }
            }
        }
    }

}
