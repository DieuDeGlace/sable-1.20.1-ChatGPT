package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.compat.IrisCompat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded diagnostics for Create's non-Flywheel contraption renderer.
 *
 * <p>Flywheel intentionally disables its backend while an Iris/Oculus shader pack is active,
 * so Create falls back to this renderer. These logs distinguish culling, dispatch/matrix, and
 * buffer-path failures without changing any rendering decision.</p>
 */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public abstract class ContraptionEntityRendererMixin {

    @Unique
    private static final Set<String> sable$CULL_STATES = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Set<String> sable$RENDER_STATES = ConcurrentHashMap.newKeySet();
    @Unique
    private static final AtomicInteger sable$LOG_COUNT = new AtomicInteger();

    @Inject(
            method = "shouldRender(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("RETURN")
    )
    private void sable$traceFallbackCulling(final AbstractContraptionEntity entity,
                                            final Frustum frustum,
                                            final double cameraX,
                                            final double cameraY,
                                            final double cameraZ,
                                            final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);
        if (subLevel == null || sable$LOG_COUNT.get() >= 96) {
            return;
        }

        final boolean shaders = IrisCompat.isShaderPackInUse();
        final boolean shadow = IrisCompat.isRenderingShadowPass();
        final boolean visible = cir.getReturnValueZ();
        final String state = entity.getUUID() + ":" + shaders + ":" + shadow + ":" + visible;
        if (!sable$CULL_STATES.add(state)) {
            return;
        }

        sable$LOG_COUNT.incrementAndGet();
        final Vector3d projected = sable$projectedPosition(entity, subLevel,
                Minecraft.getInstance().getFrameTime());
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-CREATE-CONTRAPTION-FALLBACK-CULL: uuid={}, entityId={}, shaders={}, "
                        + "shadowPass={}, visible={}, flywheelVisualization={}, plotPosition={}, "
                        + "projectedPosition={}, camera=({}, {}, {}), projectedDistanceSquared={}",
                entity.getUUID(), entity.getId(), shaders, shadow, visible,
                VisualizationManager.supportsVisualization(entity.level()), entity.position(), projected,
                cameraX, cameraY, cameraZ,
                projected.distanceSquared(cameraX, cameraY, cameraZ));
    }

    @Inject(
            method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void sable$traceFallbackRender(final AbstractContraptionEntity entity,
                                           final float yaw,
                                           final float partialTicks,
                                           final PoseStack poseStack,
                                           final MultiBufferSource buffers,
                                           final int packedLight,
                                           final CallbackInfo ci) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);
        if (subLevel == null || sable$LOG_COUNT.get() >= 96) {
            return;
        }

        final boolean shaders = IrisCompat.isShaderPackInUse();
        final boolean shadow = IrisCompat.isRenderingShadowPass();
        final boolean visualization = VisualizationManager.supportsVisualization(entity.level());
        final String state = entity.getUUID() + ":" + shaders + ":" + shadow + ":" + visualization;
        if (!sable$RENDER_STATES.add(state)) {
            return;
        }

        sable$LOG_COUNT.incrementAndGet();
        final Vector3d projected = sable$projectedPosition(entity, subLevel, partialTicks);
        final Vector3d camera = new Vector3d(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().x,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().z);
        final Matrix4f pose = poseStack.last().pose();
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-CREATE-CONTRAPTION-FALLBACK-RENDER: uuid={}, entityId={}, shaders={}, "
                        + "shadowPass={}, flywheelVisualization={}, blocks={}, plotPosition={}, "
                        + "projectedPosition={}, mainCamera={}, poseTranslation=({}, {}, {}), poseDet={}, "
                        + "bufferClass={}, packedLight={}",
                entity.getUUID(), entity.getId(), shaders, shadow, visualization,
                entity.getContraption() == null ? -1 : entity.getContraption().getBlocks().size(),
                entity.position(), projected, camera, pose.m30(), pose.m31(), pose.m32(), pose.determinant(),
                buffers.getClass().getName(), packedLight);
    }

    @Unique
    private static Vector3d sable$projectedPosition(final AbstractContraptionEntity entity,
                                                     final ClientSubLevel subLevel,
                                                     final float partialTicks) {
        final Vector3d local = new Vector3d(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()));
        return subLevel.renderPose(partialTicks).transformPosition(local);
    }
}
