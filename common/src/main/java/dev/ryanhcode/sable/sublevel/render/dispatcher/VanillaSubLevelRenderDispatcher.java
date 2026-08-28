package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

    private final Set<RenderType> singleBlockLayers;

    public VanillaSubLevelRenderDispatcher() {
        this.singleBlockLayers = new LinkedHashSet<>();
    }

    public static void setupDynamicEffects(final ShaderInstance shader, final boolean onSubLevel, final boolean upload,
                                           final @Nullable Matrix4fc baseModelView) {
        final boolean enableDynamicShading = onSubLevel && SableDynamicDirectionalShading.isEnabled();
        final Uniform sableEnableNormalLighting = shader.getUniform("SableEnableNormalLighting");
        if (sableEnableNormalLighting != null) {
            sableEnableNormalLighting.set(enableDynamicShading ? 1.0F : 0.0F);
            if (upload) {
                sableEnableNormalLighting.upload();
            }
        }

        if (enableDynamicShading && sableEnableNormalLighting != null && baseModelView != null) {
            final Uniform inverseNormalMatrix = shader.getUniform("SableInverseNormalMat");
            if (inverseNormalMatrix != null) {
                inverseNormalMatrix.set(new Matrix4f(baseModelView).normal(new Matrix3f()).invert());
                if (upload) {
                    inverseNormalMatrix.upload();
                }
            }

            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                setFloat(shader, "SableShadeDown", minecraft.level.getShade(Direction.DOWN, true), upload);
                setFloat(shader, "SableShadeUp", minecraft.level.getShade(Direction.UP, true), upload);
                setFloat(shader, "SableShadeNorth", minecraft.level.getShade(Direction.NORTH, true), upload);
                setFloat(shader, "SableShadeSouth", minecraft.level.getShade(Direction.SOUTH, true), upload);
                setFloat(shader, "SableShadeWest", minecraft.level.getShade(Direction.WEST, true), upload);
                setFloat(shader, "SableShadeEast", minecraft.level.getShade(Direction.EAST, true), upload);
            }
        }

        final Uniform sableSkyLightScale = shader.getUniform("SableSkyLightScale");

        if (sableSkyLightScale != null) {
            sableSkyLightScale.set(1.0f);
            if (upload) {
                sableSkyLightScale.upload();
            }
        }
    }

    private static void setFloat(final ShaderInstance shader, final String name, final float value,
                                 final boolean upload) {
        final Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            return;
        }
        uniform.set(value);
        if (upload) {
            uniform.upload();
        }
    }

    /**
     * Checks if this sub-level is a single block, and therefore can use simpler batched rendering
     */
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final boolean isSingle = bounds != null && bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ();
        if (!isSingle) {
            return false;
        }

        final BlockState blockState = subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        return !blockState.is(SableTags.ALWAYS_CHUNK_RENDERING);
    }

    @Override
    public void onResourceManagerReload(@NotNull final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        if (renderData instanceof VanillaSingleSubLevelRenderData ^ isSingleBlock(subLevel)) {
            renderData.close();

            // Force-rebuild the data
            final SubLevelRenderData data = this.createRenderData(subLevel);
            if (data instanceof VanillaChunkedSubLevelRenderData) {
                data.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
            }

            return data;
        }

        if (renderData instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
            chunkedRenderData.resize();
            chunkedRenderData.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
        }
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        final ChunkRenderDispatcher sectionRenderDispatcher = Minecraft.getInstance().levelRenderer.getChunkRenderDispatcher();
        return new VanillaChunkedSubLevelRenderData(subLevel, sectionRenderDispatcher);
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final CullFrustum cullFrustum, final boolean isSpectator) {
        // TODO
    }

    @Override
    public void renderSectionLayer(final Iterable<ClientSubLevel> sublevels, final RenderType renderType, final ShaderInstance shader, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        final FogShape fogShape = RenderSystem.getShaderFogShape();

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(FogShape.SPHERE.getIndex());
            shader.FOG_SHAPE.upload();
        }

        VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, true, true, modelView);

        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            // We'll render the single block sub-levels in a pass afterward
            if (!(data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData)) {
                this.singleBlockLayers.remove(renderType);
                this.singleBlockLayers.add(renderType);
                continue;
            }

            chunkedRenderData.renderChunkedSubLevel(
                    renderType, shader, modelView, cameraX, cameraY, cameraZ);
        }

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(fogShape.getIndex());
        }

        VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, false, false, null);
    }

    @Override
    public void renderAfterSections(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        if (this.singleBlockLayers.isEmpty()) {
            return;
        }

        SableDynamicDirectionalShading.setCompilingSubLevel(true);
        try {
            for (final RenderType layer : this.singleBlockLayers) {
                final BufferBuilder consumer = Tesselator.getInstance().getBuilder();
                consumer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

                for (final ClientSubLevel sublevel : sublevels) {
                    final SubLevelRenderData data = sublevel.getRenderData();

                    if (!(data instanceof final VanillaSingleSubLevelRenderData singleRenderData)) {
                        continue;
                    }

                    singleRenderData.renderSingleBlock(layer, consumer, modelView, cameraX, cameraY, cameraZ);
                }

                final BufferBuilder.RenderedBuffer renderedBuffer = consumer.endOrDiscardIfEmpty();
                if (renderedBuffer != null) {
                    // Set up the state so the shader instance is updated
                    layer.setupRenderState();

                    final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader());
                    if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelView);
                    if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
                    if (shader.SCREEN_SIZE != null) shader.SCREEN_SIZE.set(
                            Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
                    shader.apply();
                    setupDynamicEffects(shader, true, true, modelView);

                    BufferUploader.drawWithShader(renderedBuffer);

                    setupDynamicEffects(shader, false, true, null);

                    // RenderType owns shader binding, so its clear is the matching teardown.
                    layer.clearRenderState();
                }
            }
        } finally {
            SableDynamicDirectionalShading.setCompilingSubLevel(false);
            this.singleBlockLayers.clear();
        }
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        this.renderBlockEntities(sublevels, blockEntityRenderer, cameraX, cameraY, cameraZ,
                partialTick, new Matrix4f());
    }

    /**
     * Minecraft 1.20.1 supplies camera rotation through the LevelRenderer PoseStack.
     * Forge render-stage adapters must preserve that base view matrix before applying a
     * sub-level's physical transform; starting from identity submits block entities in
     * untranslated camera axes and makes them disappear as the view rotates.
     */
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels,
                                    final BlockEntityRenderer blockEntityRenderer,
                                    final double cameraX, final double cameraY, final double cameraZ,
                                    final float partialTick, final Matrix4fc baseModelView) {
        final Vector3d chunkOffset = new Vector3d();
        final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final PoseStack matrices = new PoseStack();
        final Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);

        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();
            final Pose3dc renderPose = sublevel.renderPose();

            renderPose.rotationPoint().negate(chunkOffset.zero());

            // Do not recover the local camera by inverting a float matrix whose
            // translation is based on Sable's ~20,000,000-block plot coordinates.
            // That loses whole-block precision. Pose3d performs the inverse in
            // doubles and produces the exact coordinate used by shouldRender().
            final Vec3 localCamera = renderPose.transformPositionInverse(cameraPosition);
            dispatcher.sable$setCameraPosition(localCamera);

            matrices.last().pose().set(baseModelView);
            matrices.last().normal().set(new org.joml.Matrix3f(baseModelView));
            matrices.translate(renderPose.position().x() - cameraX,
                    renderPose.position().y() - cameraY,
                    renderPose.position().z() - cameraZ);
            matrices.mulPose(new Quaternionf(renderPose.orientation()));
            matrices.scale((float) renderPose.scale().x(),
                    (float) renderPose.scale().y(),
                    (float) renderPose.scale().z());
            if (data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
                for (final ChunkRenderDispatcher.RenderChunk renderSection : chunkedRenderData.allRenderSections()) {
                    final List<BlockEntity> blockEntities = renderSection.getCompiledChunk().getRenderableBlockEntities();
                    if (!blockEntities.isEmpty()) {
                        blockEntityRenderer.renderBlockEntities(blockEntities, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                    }
                }
            } else if (data instanceof final VanillaSingleSubLevelRenderData singleRenderData) {
                final BlockEntity renderBlockEntity = singleRenderData.getRenderBlockEntity();
                if (renderBlockEntity != null) {
                    blockEntityRenderer.renderSingleBE(renderBlockEntity, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                }
            }
        }

        dispatcher.sable$setCameraPosition(null);
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
