package dev.ryanhcode.sable.mixin.port.sublevel_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Set;
import java.util.SortedSet;

/** Minecraft 1.20.1 signature adapter for the upstream render-level hooks. */
// Oculus 1.8.0 applies its entity-list local-variable mixin at priority 999.
// Applying this adapter afterwards preserves the unmodified local table that
// Oculus requires while retaining Sable's upstream render stage.
@Mixin(value = LevelRenderer.class, priority = 900)
public class LevelRendererMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Shadow
    @Final
    private Set<BlockEntity> globalBlockEntities;

    @Unique
    private VanillaSubLevelBlockEntityRenderer sable$blockEntityRenderer;

    /**
     * Forge/Minecraft 1.20.1 checks the main-world compiled chunk for every entity after
     * frustum culling and before dispatching its renderer. Plot-space entities must use
     * their projected physical position for that check; otherwise Create's non-Flywheel
     * contraption fallback is skipped while Oculus has disabled the Flywheel backend.
     */
    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z"),
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V")))
    private boolean sable$projectEntityCompiledChunk(final LevelRenderer instance,
                                                      final BlockPos plotPosition,
                                                      final Operation<Boolean> original,
                                                      @Local final Entity entity,
                                                      @Local(argsOnly = true) final float partialTick) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);
        if (subLevel == null) {
            return original.call(instance, plotPosition);
        }

        final Vec3 projectedPosition = subLevel.renderPose(partialTick).transformPosition(entity.position());
        final BlockPos projectedBlockPosition = BlockPos.containing(projectedPosition);
        return original.call(instance, projectedBlockPosition);
    }

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void sable$allChanged(final CallbackInfo ci) {
        if (this.level == null) {
            return;
        }

        final ClientSubLevelContainer container = (ClientSubLevelContainer)
                ((SubLevelContainerHolder) this.level).sable$getPlotContainer();
        SubLevelRenderDispatcher.get().rebuild(container.getAllSubLevels());
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;constantAmbientLight()Z",
            ordinal = 0))
    private boolean sable$renderSingleBlockSubLevels(final DimensionSpecialEffects effects,
                                                       final PoseStack poseStack,
                                                       final float partialTick,
                                                       final long finishNanoTime,
                                                       final boolean renderBlockOutline,
                                                       final Camera camera,
                                                       final GameRenderer gameRenderer,
                                                       final LightTexture lightTexture,
                                                       final Matrix4f projection) {
        if (this.level != null) {
            final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer)
                    ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
            final Vec3 cameraPosition = camera.getPosition();
            SubLevelRenderDispatcher.get().renderAfterSections(sublevels,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z,
                    new Matrix4f(poseStack.last().pose()), projection, partialTick);
        }
        return effects.constantAmbientLight();
    }

    /**
     * Forge 1.20.1 equivalent of upstream's block-entity hook. It runs after
     * normal compiled block entities and immediately before vanilla enters the
     * global block-entity set, while the level-owned MultiBufferSource is still
     * in its native collection phase. Oculus therefore retains ownership of
     * finalization; no late, out-of-band endBatch call is needed.
     */
    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;globalBlockEntities:Ljava/util/Set;",
            ordinal = 0))
    private Set<BlockEntity> sable$renderSubLevelBlockEntities(final LevelRenderer owner,
                                                                final PoseStack poseStack,
                                                                final float partialTick,
                                                                final long finishNanoTime,
                                                                final boolean renderBlockOutline,
                                                                final Camera camera,
                                                                final GameRenderer gameRenderer,
                                                                final LightTexture lightTexture,
                                                                final Matrix4f projection) {
        if (this.level != null) {
            final SubLevelContainer rawContainer = SubLevelContainer.getContainer(this.level);
            if (rawContainer instanceof final ClientSubLevelContainer container) {
                if (this.sable$blockEntityRenderer == null) {
                    this.sable$blockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(
                            net.minecraft.client.Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            this.renderBuffers, this.destructionProgress);
                }

                final Vec3 cameraPosition = camera.getPosition();
                ((VanillaSubLevelRenderDispatcher) SubLevelRenderDispatcher.get()).renderBlockEntities(
                        container.getAllSubLevels(), this.sable$blockEntityRenderer,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick,
                        new Matrix4f(poseStack.last().pose()));
            }
        }
        return this.globalBlockEntities;
    }
}
