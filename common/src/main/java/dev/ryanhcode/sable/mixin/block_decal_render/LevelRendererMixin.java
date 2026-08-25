package dev.ryanhcode.sable.mixin.block_decal_render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Changes the distance block damage is rendered from, and transforms block damage rendering for sublevels.
 */
// Oculus 1.8.0 modifies renderLevel's entity iterator at priority 999.
// Apply afterwards so its implicit local-variable discriminator sees vanilla's table.
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererMixin {

    // Storage vectors to avoid repeated allocation
    private final @Unique Quaternionf sable$orientationStorage = new Quaternionf();

    @Shadow
    @Nullable
    private ClientLevel level;

    @Redirect(
            method = "renderLevel",
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/renderer/LevelRenderer;destructionProgress:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"
            )
    )
    private void sable$translateBlockDamage(final PoseStack ps,
                                            final double plotToCameraX,
                                            final double plotToCameraY,
                                            final double plotToCameraZ) {
        final Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final BlockPos pos = new BlockPos(
                (int) Math.round(plotToCameraX + cameraPos.x),
                (int) Math.round(plotToCameraY + cameraPos.y),
                (int) Math.round(plotToCameraZ + cameraPos.z)
        );
        final Vec3 plotPos = Vec3.atLowerCornerOf(pos);
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, plotPos);

        if (subLevel == null) {
            ps.translate(plotToCameraX, plotToCameraY, plotToCameraZ);
            return;
        }

        final Pose3dc renderPose = subLevel.renderPose();
        final Vec3 projectedPos = renderPose.transformPosition(plotPos);

        ps.translate(projectedPos.x - cameraPos.x, projectedPos.y - cameraPos.y, projectedPos.z - cameraPos.z);
        ps.mulPose(this.sable$orientationStorage.set(renderPose.orientation()));
    }

    @ModifyConstant(method = "renderLevel", constant = @Constant(doubleValue = 1024.0, ordinal = 0))
    private double sable$blockDamageDistance(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }
}
