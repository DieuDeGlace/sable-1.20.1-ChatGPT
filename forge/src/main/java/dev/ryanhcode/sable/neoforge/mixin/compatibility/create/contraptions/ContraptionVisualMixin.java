package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.EmbeddedEnvironmentExtension;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixes the rendering of contraption visuals on sub-levels
 */
@Mixin(ContraptionVisual.class)
public abstract class ContraptionVisualMixin extends AbstractEntityVisual<AbstractContraptionEntity> {

    private static final Set<String> SABLE$LOGGED_VISUAL_STATES = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    protected VisualEmbedding embedding;
    @Shadow
    @Final
    private PoseStack contraptionMatrix;

    public ContraptionVisualMixin(final VisualizationContext ctx, final AbstractContraptionEntity entity, final float partialTick) {
        super(ctx, entity, partialTick);
    }

    @Inject(method = "setEmbeddingMatrices", at = @At(value = "HEAD"), cancellable = true)
    private void sable$setEmbeddingMatrices(final float partialTick, final CallbackInfo ci) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.entity.level());

        if (container == null)
            return;

        final ChunkPos chunkPos = this.entity.chunkPosition();
        final boolean inBounds = container.inBounds(chunkPos);

        if (!inBounds) {
            sable$logVisualState("outside-plot", chunkPos, null, null);
            return;
        }

        final int plotX = (chunkPos.x >> container.getLogPlotSize()) - container.getOrigin().x;
        final int plotZ = (chunkPos.z >> container.getLogPlotSize()) - container.getOrigin().y;

        final FlywheelCompatNeoForge.SubLevelFlwRenderState state = FlywheelCompatNeoForge.getInfo(ChunkPos.asLong(plotX, plotZ));

        if (state == null) {
            sable$logVisualState("render-state-missing", chunkPos, plotX, plotZ);
            return;
        }

        final Vec3i origin = this.renderOrigin();

        final Vector3d pos = new Vector3d();
        if (this.entity.isPrevPosInvalid()) {
            pos.x = this.entity.getX();
            pos.y = this.entity.getY();
            pos.z = this.entity.getZ();
        } else {
            pos.x = Mth.lerp(partialTick, this.entity.xo, this.entity.getX());
            pos.y = Mth.lerp(partialTick, this.entity.yo, this.entity.getY());
            pos.z = Mth.lerp(partialTick, this.entity.zo, this.entity.getZ());
        }

        final ChunkPos centerChunk = state.centerChunk;
        final PoseStack sceneMatrix = new PoseStack();
        sceneMatrix.translate(
                (float) (pos.x - centerChunk.getMinBlockX()),
                (float) pos.y,
                (float) (pos.z - centerChunk.getMinBlockZ())
        );
        this.entity.applyLocalTransforms(sceneMatrix, partialTick);

        final Pose3dc renderPose = state.renderPose;
        renderPose.transformPosition(pos).sub(origin.getX(), origin.getY(), origin.getZ());

        this.contraptionMatrix.setIdentity();
        this.contraptionMatrix.translate(pos.x, pos.y, pos.z);
        this.contraptionMatrix.mulPose(new Quaternionf(renderPose.orientation()));
        this.entity.applyLocalTransforms(this.contraptionMatrix, partialTick);
        this.embedding.transforms(this.contraptionMatrix.last().pose(), this.contraptionMatrix.last().normal());

        if (this.embedding instanceof final EmbeddedEnvironmentExtension extension) {
            extension.sable$setLightingInfo(sceneMatrix.last().pose(), state.sceneID, state.latestSkyLightScale / 15.0f);
        }
        sable$logVisualState("embedded", chunkPos, plotX, plotZ);
        ci.cancel();
    }

    private void sable$logVisualState(final String branch, final ChunkPos chunkPos,
                                      final Integer plotX, final Integer plotZ) {
        final String key = this.entity.getUUID() + ":" + branch;
        if (SABLE$LOGGED_VISUAL_STATES.size() >= 192 || !SABLE$LOGGED_VISUAL_STATES.add(key)) {
            return;
        }
        final int blockCount = this.entity.getContraption() == null
                ? -1 : this.entity.getContraption().getBlocks().size();
        final float poseDet = this.contraptionMatrix.last().pose().determinant();
        final float normalDet = this.contraptionMatrix.last().normal().determinant();
        dev.ryanhcode.sable.Sable.LOGGER.info(
                "SABLE-CREATE-CONTRAPTION-VISUAL: branch={}, uuid={}, entityId={}, chunk={}, plot=({},{}), "
                        + "position=[{},{},{}], blocks={}, removed={}, poseDet={}, normalDet={}",
                branch, this.entity.getUUID(), this.entity.getId(), chunkPos, plotX, plotZ,
                this.entity.getX(), this.entity.getY(), this.entity.getZ(), blockCount,
                this.entity.isRemoved(), poseDet, normalDet);
    }
}
