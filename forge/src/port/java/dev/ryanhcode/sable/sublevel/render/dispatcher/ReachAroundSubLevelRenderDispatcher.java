package dev.ryanhcode.sable.sublevel.render.dispatcher;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

/**
 * Minecraft 1.20.1 equivalent of upstream Sable's Sodium reach-around renderer.
 *
 * <p>Embeddium replaces vanilla terrain compilation, so sub-level chunks need
 * their own vanilla dispatcher. In particular, its synchronous builder pack
 * must not be Minecraft's RenderBuffers.fixedBufferPack(), which is also used
 * by block-entity and GUI rendering on this target.</p>
 */
public final class ReachAroundSubLevelRenderDispatcher extends VanillaSubLevelRenderDispatcher {

    private final int asynchronousBuilderCount;
    private ChunkBufferBuilderPack fixedBufferPack;
    private ChunkRenderDispatcher sectionRenderDispatcher;

    public ReachAroundSubLevelRenderDispatcher() {
        this.asynchronousBuilderCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    }

    private ChunkRenderDispatcher getSectionRenderDispatcher(final LevelRenderer levelRenderer,
                                                              final ClientLevel level) {
        if (this.sectionRenderDispatcher == null) {
            final Minecraft minecraft = Minecraft.getInstance();
            this.fixedBufferPack = new ChunkBufferBuilderPack();
            this.sectionRenderDispatcher = new ChunkRenderDispatcher(
                    level, levelRenderer, Util.backgroundExecutor(), minecraft.is64Bit(),
                    this.fixedBufferPack, this.asynchronousBuilderCount);
            Sable.LOGGER.info("SABLE-EMBEDDIUM-REACHAROUND: dedicatedDispatcher=true, "
                            + "dedicatedFixedBuffers=true, asyncBuilders={}",
                    this.asynchronousBuilderCount);
        }

        this.sectionRenderDispatcher.setLevel(level);
        return this.sectionRenderDispatcher;
    }

    @Override
    public void rebuild(final Iterable<ClientSubLevel> sublevels) {
        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.blockUntilClear();
        }
        super.rebuild(sublevels);
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final ChunkRenderDispatcher dispatcher = this.getSectionRenderDispatcher(
                minecraft.levelRenderer, subLevel.getLevel());
        return new VanillaChunkedSubLevelRenderData(subLevel, dispatcher);
    }

    @Override
    public void preRenderChunks(final Camera camera) {
        if (this.sectionRenderDispatcher == null) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        this.sectionRenderDispatcher.setCamera(camera.getPosition());
        minecraft.getProfiler().push("sub_level_upload");
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        minecraft.getProfiler().pop();
    }

    @Override
    public void free() {
        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.dispose();
            this.sectionRenderDispatcher = null;
        }
        if (this.fixedBufferPack != null) {
            this.fixedBufferPack.discardAll();
            this.fixedBufferPack = null;
        }
        super.free();
    }
}
