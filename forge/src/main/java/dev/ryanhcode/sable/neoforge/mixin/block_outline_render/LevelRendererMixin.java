package dev.ryanhcode.sable.neoforge.mixin.block_outline_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Transforms block hover outlines for sublevels.
 */
// Oculus 1.8.0 modifies renderLevel's entity iterator at priority 999.
// Apply afterwards so its implicit local-variable discriminator sees vanilla's table.
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererMixin {

    // Storage vectors to avoid repeated allocation
    private final @Unique Quaternionf sable$orientationStorage = new Quaternionf();
    private final @Unique SubLevelCamera sable$sublevelCamera = new SubLevelCamera();

    @Shadow
    @Nullable
    private ClientLevel level;

    @Invoker("renderHitOutline")
    protected abstract void sable$invokeRenderHitOutline(PoseStack poseStack, VertexConsumer consumer,
                                                          Entity entity, double cameraX, double cameraY, double cameraZ,
                                                          BlockPos blockPos, BlockState blockState);

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onDrawHighlight(Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/HitResult;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Z"))
    private boolean sable$renderSubLevelHighlight(final LevelRenderer context,
                                                   final Camera camera,
                                                   final HitResult target,
                                                   final float partialTick,
                                                   final PoseStack poseStack,
                                                   final MultiBufferSource bufferSource) {
        if (!(target instanceof final BlockHitResult blockTarget)) {
            return ForgeHooksClient.onDrawHighlight(context, camera, target, partialTick, poseStack, bufferSource);
        }

        final BlockPos blockPos = blockTarget.getBlockPos();
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);

        if (subLevel == null) {
            return ForgeHooksClient.onDrawHighlight(context, camera, target, partialTick, poseStack, bufferSource);
        }

        poseStack.pushPose();
        try {
            this.sable$applySubLevelPose(camera, subLevel.renderPose(), poseStack);
            return ForgeHooksClient.onDrawHighlight(context, this.sable$sublevelCamera, target,
                    partialTick, poseStack, bufferSource);
        } finally {
            poseStack.popPose();
            this.sable$sublevelCamera.clear();
        }
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void sable$renderSubLevelHitOutline(final LevelRenderer context,
                                                final PoseStack poseStack,
                                                final VertexConsumer consumer,
                                                final Entity entity,
                                                final double cameraX,
                                                final double cameraY,
                                                final double cameraZ,
                                                final BlockPos blockPos,
                                                final BlockState blockState) {
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);
        if (subLevel == null) {
            this.sable$invokeRenderHitOutline(poseStack, consumer, entity, cameraX, cameraY, cameraZ,
                    blockPos, blockState);
            return;
        }

        final Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        try {
            this.sable$applySubLevelPose(camera, subLevel.renderPose(), poseStack);
            final Vec3 localCamera = this.sable$sublevelCamera.getPosition();
            this.sable$invokeRenderHitOutline(poseStack, consumer, entity,
                    localCamera.x, localCamera.y, localCamera.z, blockPos, blockState);
        } finally {
            poseStack.popPose();
            this.sable$sublevelCamera.clear();
        }
    }

    @Unique
    private void sable$applySubLevelPose(final Camera camera, final Pose3dc pose, final PoseStack poseStack) {
        this.sable$sublevelCamera.setCamera(camera);
        this.sable$sublevelCamera.setPose(pose);
        final Vec3 cameraPosition = this.sable$sublevelCamera.getPosition();
        final Vec3 realCameraPosition = camera.getPosition();
        final Vector3dc position = pose.position();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Quaterniondc orientation = pose.orientation();
        final Vector3dc scale = pose.scale();

        poseStack.translate(
                (float) (position.x() - realCameraPosition.x),
                (float) (position.y() - realCameraPosition.y),
                (float) (position.z() - realCameraPosition.z)
        );
        poseStack.mulPose(this.sable$orientationStorage.set(orientation));
        poseStack.translate(
                (float) -(rotationPoint.x() - cameraPosition.x),
                (float) -(rotationPoint.y() - cameraPosition.y),
                (float) -(rotationPoint.z() - cameraPosition.z)
        );
        poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}
