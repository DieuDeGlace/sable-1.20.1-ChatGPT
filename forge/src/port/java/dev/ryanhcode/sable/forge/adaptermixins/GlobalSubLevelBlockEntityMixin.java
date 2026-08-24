package dev.ryanhcode.sable.neoforge.mixin.admitted;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 adaptation of Sable's global block-entity transform path.
 * Normal compiled block entities are rendered by the Forge stage bridge;
 * this redirect covers renderers whose shouldRenderOffScreen() is true.
 */
@Mixin(LevelRenderer.class)
public abstract class GlobalSubLevelBlockEntityMixin {
    @Unique private static final Logger sable$LOGGER = LogUtils.getLogger();
    @Unique private static final Set<String> sable$loggedRenderPaths = ConcurrentHashMap.newKeySet();
    @Unique private static final Set<String> sable$loggedCullPaths = ConcurrentHashMap.newKeySet();
    @Unique private final Matrix4f sable$baseWorldPose = new Matrix4f();
    @Unique private final Matrix3f sable$baseWorldNormal = new Matrix3f();

    /**
     * Capture the camera/view basis before vanilla applies any per-block translation.
     * Plot coordinates are around twenty million blocks; trying to undo vanilla's
     * float translation after it happened loses several blocks of precision.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sable$captureWorldRenderBasis(final PoseStack poseStack,
                                               final float partialTick,
                                               final long finishNanoTime,
                                               final boolean renderBlockOutline,
                                               final Camera camera,
                                               final GameRenderer gameRenderer,
                                               final LightTexture lightTexture,
                                               final Matrix4f projectionMatrix,
                                               final CallbackInfo ci) {
        this.sable$baseWorldPose.set(poseStack.last().pose());
        this.sable$baseWorldNormal.set(poseStack.last().normal());
    }

    /**
     * Forge 1.20.1 adds a frustum test in front of the global block-entity
     * renderer. Sub-level block entities are stored at plot coordinates, so
     * testing their unprojected box rejects them before Sable's render redirect
     * can apply the physical pose. This is the 1.20.1 equivalent of upstream's
     * block-entity visibility projection.
     */
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z",
                    ordinal = 1
            )
    )
    private boolean sable$projectGlobalBlockEntityBounds(final Frustum frustum, final AABB renderBounds) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(renderBounds.getCenter());
        if (subLevel == null) {
            return frustum.isVisible(renderBounds);
        }

        final AABB physicalBounds = new BoundingBox3d(renderBounds)
                .transform(subLevel.logicalPose())
                .toMojang();
        final boolean visible = frustum.isVisible(physicalBounds);
        final String diagnosticKey = subLevel.getUniqueId() + ":" + renderBounds;
        if (sable$loggedCullPaths.size() < 64 && sable$loggedCullPaths.add(diagnosticKey)) {
            sable$LOGGER.info("SABLE-BE-GLOBAL-CULL: sub-level={}, plotBounds={}, physicalBounds={}, visible={}",
                    subLevel.getUniqueId(), renderBounds, physicalBounds, visible);
        }
        return visible;
    }

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
                    ordinal = 1
            )
    )
    private <E extends BlockEntity> void sable$renderGlobalBlockEntity(
            final BlockEntityRenderDispatcher dispatcher,
            final E blockEntity,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource
    ) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null || subLevel.getRenderData() == null) {
            dispatcher.render(blockEntity, partialTick, poseStack, bufferSource);
            return;
        }

        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final BlockPos blockPos = blockEntity.getBlockPos();
        final Pose3dc renderPose = subLevel.renderPose();
        final Vec3 localCamera = renderPose.transformPositionInverse(camera);
        final BlockEntityRenderDispatcherExtension extension = (BlockEntityRenderDispatcherExtension) dispatcher;
        final PoseStack physicalPoseStack = new PoseStack();

        try {
            physicalPoseStack.last().pose().set(this.sable$baseWorldPose);
            physicalPoseStack.last().normal().set(this.sable$baseWorldNormal);
            physicalPoseStack.translate(renderPose.position().x() - camera.x,
                    renderPose.position().y() - camera.y,
                    renderPose.position().z() - camera.z);
            physicalPoseStack.mulPose(new Quaternionf(renderPose.orientation()));
            physicalPoseStack.scale((float) renderPose.scale().x(),
                    (float) renderPose.scale().y(), (float) renderPose.scale().z());
            physicalPoseStack.translate(blockPos.getX() - renderPose.rotationPoint().x(),
                    blockPos.getY() - renderPose.rotationPoint().y(),
                    blockPos.getZ() - renderPose.rotationPoint().z());
            extension.sable$setCameraPosition(localCamera);
            dispatcher.render(blockEntity, partialTick, physicalPoseStack, bufferSource);

            final String diagnosticKey = subLevel.getUniqueId() + ":" + blockEntity.getType();
            if (sable$loggedRenderPaths.size() < 64 && sable$loggedRenderPaths.add(diagnosticKey)) {
                final Vec3 physicalBlockOrigin = renderPose.transformPosition(Vec3.atLowerCornerOf(blockPos));
                sable$LOGGER.info("SABLE-BE-GLOBAL-RENDER: sub-level={}, type={}, local={}, physical={}, "
                                + "camera={}, localCamera={}, precision-safe=true",
                        subLevel.getUniqueId(), blockEntity.getType(), blockPos, physicalBlockOrigin,
                        camera, localCamera);
            }
        } finally {
            extension.sable$setCameraPosition(null);
        }
    }
}
