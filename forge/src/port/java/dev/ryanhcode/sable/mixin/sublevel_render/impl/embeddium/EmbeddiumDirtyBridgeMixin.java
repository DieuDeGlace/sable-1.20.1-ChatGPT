package dev.ryanhcode.sable.mixin.sublevel_render.impl.embeddium;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries Embeddium's chunk rebuild notifications into Sable's vanilla
 * sub-level render data.
 *
 * <p>Embeddium replaces the vanilla {@code ViewArea#setDirty} path, so the
 * vanilla bridge cannot observe block changes while Embeddium is installed.
 * The target and injection point are the Forge 1.20.1 equivalents of Sable's
 * upstream Sodium rebuild hook.</p>
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class EmbeddiumDirtyBridgeMixin {

    private static final AtomicLong SABLE$BRIDGED_REBUILDS = new AtomicLong();
    private static final AtomicBoolean SABLE$LOGGED_NATIVE_RENDER = new AtomicBoolean();
    private static final AtomicBoolean SABLE$LOGGED_TERRAIN_LIFECYCLE = new AtomicBoolean();

    /**
     * Target adaptation of upstream SodiumWorldRendererMixin#sable$setupTerrain:
     * drain the dedicated reach-around dispatcher, then compile dirty Sable
     * sections in Embeddium's terrain lifecycle instead of relying on vanilla.
     */
    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;"
            + "Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;IZZ)V",
            at = @At("TAIL"), remap = false)
    private void sable$compileSubLevelSections(final CallbackInfo ci,
                                                final @Local(argsOnly = true) Camera camera) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        dispatcher.preRenderChunks(camera);

        final RenderRegionCache renderRegionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates chunkUpdates = minecraft.options.prioritizeChunkUpdates().get();
        for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
            subLevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
        }

        if (SABLE$LOGGED_TERRAIN_LIFECYCLE.compareAndSet(false, true)) {
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-EMBEDDIUM-REACHAROUND-TERRAIN: preRenderDrain=true, compile=true");
        }
    }

    /**
     * Embeddium 0.3.31 overwrites {@code LevelRenderer#renderChunkLayer} and
     * renders terrain through this method, so the vanilla pre-shader-clear
     * injection point does not exist. This is the 1.20.1 equivalent of
     * upstream Sable's Sodium {@code drawChunkLayer} tail hook: establish the
     * RenderType shader state, draw Sable's vanilla-format section buffers,
     * then perform the matching teardown.
     */
    @Inject(method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
            at = @At("TAIL"), remap = false)
    private void sable$drawSubLevelSections(final RenderType renderType, final PoseStack poseStack,
                                            final double cameraX, final double cameraY, final double cameraZ,
                                            final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final Matrix4f modelView = new Matrix4f(poseStack.last().pose());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        renderType.setupRenderState();
        final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(),
                "Embeddium RenderType did not select a shader");
        try {
            // Exact Forge 1.20.1 LevelRenderer#renderChunkLayer shader setup.
            // ShaderInstance#setDefaultUniforms only exists on newer targets.
            for (int sampler = 0; sampler < 12; sampler++) {
                shader.setSampler("Sampler" + sampler, RenderSystem.getShaderTexture(sampler));
            }
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelView);
            if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
            if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
            if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
            if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
            if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
            if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
            if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
            if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
            RenderSystem.setupShaderLights(shader);
            shader.apply();

            if (SABLE$LOGGED_NATIVE_RENDER.compareAndSet(false, true)) {
                dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "[SABLE-EMBEDDIUM-NATIVE-RENDER] drawChunkLayerTail=true shader={}",
                        shader.getName());
            }
            SubLevelRenderDispatcher.get().renderSectionLayer(container.getAllSubLevels(), renderType, shader,
                    cameraX, cameraY, cameraZ, modelView, projection, minecraft.getPartialTick());

        } finally {
            shader.clear();
            renderType.clearRenderState();
        }
    }

    @Inject(method = "scheduleRebuildForChunk(IIIZ)V", at = @At("TAIL"), remap = false)
    private void sable$bridgeSubLevelRebuild(final int x, final int y, final int z,
                                              final boolean playerChanged, final CallbackInfo ci) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(x, z)) {
            return;
        }

        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(level, new ChunkPos(x, z));
        if (subLevel == null) {
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.warn(Sable.LOGGER, "SABLE-EMBEDDIUM-DIRTY-MISS: chunk=({}, {}, {}), playerChanged={}",
                    x, y, z, playerChanged);
            return;
        }

        subLevel.getRenderData().setDirty(x, y, z, playerChanged);

        final long bridged = SABLE$BRIDGED_REBUILDS.incrementAndGet();
        if (bridged <= 16 || (bridged & (bridged - 1)) == 0) {
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-EMBEDDIUM-DIRTY-BRIDGE: count={}, chunk=({}, {}, {}), playerChanged={}",
                    bridged, x, y, z, playerChanged);
        }
    }
}
