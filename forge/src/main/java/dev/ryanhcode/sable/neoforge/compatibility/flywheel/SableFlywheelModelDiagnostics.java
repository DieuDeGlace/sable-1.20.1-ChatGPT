package dev.ryanhcode.sable.neoforge.compatibility.flywheel;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.EmptyVirtualBlockGetter;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Correlates cached Flywheel models with their partial-model build inputs. */
public final class SableFlywheelModelDiagnostics {
    private static final int EMPTY_MODEL_TRACE_LIMIT = 64;
    private static final Map<Model, PartialEvidence> PARTIALS_BY_MODEL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> TRACED_EMPTY_MODELS = new HashSet<>();

    private SableFlywheelModelDiagnostics() {
    }

    public static void recordPartial(final PartialModel partial, final Model model, final String source) {
        if (partial == null || model == null) {
            return;
        }
        synchronized (PARTIALS_BY_MODEL) {
            PARTIALS_BY_MODEL.put(model, new PartialEvidence(partial, source));
        }
        if (model.meshes().isEmpty()) {
            traceEmptyPartial(partial, model, source + ":return");
        }
    }

    public static void traceEmptyAdmission(final Model model) {
        final PartialEvidence evidence;
        synchronized (PARTIALS_BY_MODEL) {
            evidence = PARTIALS_BY_MODEL.get(model);
        }
        if (evidence == null) {
            traceUnmappedModel(model);
            return;
        }
        traceEmptyPartial(evidence.partial(), model, evidence.source() + ":admission");
    }

    private static void traceEmptyPartial(final PartialModel partial, final Model model, final String source) {
        final BakedModel bakedModel = partial.get();
        final String key = partial.modelLocation() + ":" + System.identityHashCode(bakedModel)
                + ":" + System.identityHashCode(model);
        if (!claimTrace(key)) {
            return;
        }

        try {
            if (bakedModel == null) {
                dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-PARTIAL-EMPTY: location={}, bakedClass=<null>, bakedIdentity=0, "
                                + "missingModel=false, resultClass={}, resultIdentity={}, source={}, thread={}, "
                                + "renderTypeCount=-1, renderTypes=<unavailable>",
                        partial.modelLocation(), model.getClass().getName(), System.identityHashCode(model), source,
                        Thread.currentThread().getName());
                return;
            }

            final Minecraft minecraft = Minecraft.getInstance();
            final BlockPos pos = BlockPos.ZERO;
            final BlockState state = EmptyVirtualBlockGetter.FULL_DARK.getBlockState(pos);
            final ModelData modelData = bakedModel.getModelData(
                    EmptyVirtualBlockGetter.FULL_DARK, pos, state, ModelData.EMPTY);
            final RandomSource random = RandomSource.createNewThreadLocalInstance();
            final long seed = state.getSeed(pos);
            random.setSeed(seed);
            final ChunkRenderTypeSet renderTypes = bakedModel.getRenderTypes(state, random, modelData);

            final StringBuilder typeEvidence = new StringBuilder();
            int typeIndex = 0;
            for (final RenderType renderType : renderTypes) {
                if (typeIndex++ > 0) {
                    typeEvidence.append(';');
                }
                // RenderType#toString() can execute mod shader-state code that requires the
                // render thread's GL capabilities. Models.partial() is allowed to run on a
                // Flywheel worker, so keep every field below data-only and thread-safe.
                typeEvidence.append('{')
                        .append("class=").append(renderType.getClass().getName())
                        .append(",identity=").append(System.identityHashCode(renderType))
                        .append(",chunkLayerId=").append(renderType.getChunkLayerId())
                        .append(",canonical=").append(canonicalChunkLayer(renderType))
                        .append(",materialPresent=").append(ModelUtil.getMaterial(renderType, true) != null)
                        .append(",quads=[unculled=")
                        .append(quadCount(bakedModel, state, null, random, seed, modelData, renderType));
                for (final Direction direction : Direction.values()) {
                    typeEvidence.append(',').append(direction.getName()).append('=')
                            .append(quadCount(bakedModel, state, direction, random, seed, modelData, renderType));
                }
                typeEvidence.append("]}");
            }

            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-PARTIAL-EMPTY: location={}, bakedClass={}, bakedIdentity={}, "
                            + "missingModel={}, resultClass={}, resultIdentity={}, source={}, thread={}, "
                            + "renderTypeCount={}, renderTypes={}",
                    partial.modelLocation(), bakedModel.getClass().getName(), System.identityHashCode(bakedModel),
                    bakedModel == minecraft.getModelManager().getMissingModel(), model.getClass().getName(),
                    System.identityHashCode(model), source, Thread.currentThread().getName(), typeIndex, typeEvidence);
        } catch (final Throwable failure) {
            // Diagnostics must never abort Flywheel visual creation. Keep this record free of
            // RenderType#toString() for the same worker-thread reason described above.
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-PARTIAL-EMPTY: location={}, bakedClass={}, bakedIdentity={}, "
                            + "resultClass={}, resultIdentity={}, source={}, thread={}, diagnosticFailure={}:{}",
                    partial.modelLocation(), bakedModel == null ? "<null>" : bakedModel.getClass().getName(),
                    System.identityHashCode(bakedModel), model.getClass().getName(), System.identityHashCode(model),
                    source, Thread.currentThread().getName(), failure.getClass().getName(), failure.getMessage());
        }
    }

    private static void traceUnmappedModel(final Model model) {
        final String key = "unmapped:" + System.identityHashCode(model);
        if (!claimTrace(key)) {
            return;
        }
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-PARTIAL-EMPTY: location=<unmapped>, bakedClass=<unmapped>, "
                        + "bakedIdentity=0, missingModel=false, resultClass={}, resultIdentity={}, "
                        + "source=draw-manager:unmapped, thread={}, renderTypeCount=-1, renderTypes=<unavailable>",
                model.getClass().getName(), System.identityHashCode(model), Thread.currentThread().getName());
    }

    private static boolean claimTrace(final String key) {
        synchronized (TRACED_EMPTY_MODELS) {
            return TRACED_EMPTY_MODELS.size() < EMPTY_MODEL_TRACE_LIMIT && TRACED_EMPTY_MODELS.add(key);
        }
    }

    private static int quadCount(final BakedModel bakedModel,
                                 final BlockState state,
                                 final Direction direction,
                                 final RandomSource random,
                                 final long seed,
                                 final ModelData modelData,
                                 final RenderType renderType) {
        random.setSeed(seed);
        return bakedModel.getQuads(state, direction, random, modelData, renderType).size();
    }

    private static String canonicalChunkLayer(final RenderType renderType) {
        if (renderType == RenderType.solid()) {
            return "solid";
        }
        if (renderType == RenderType.cutoutMipped()) {
            return "cutout_mipped";
        }
        if (renderType == RenderType.cutout()) {
            return "cutout";
        }
        if (renderType == RenderType.translucent()) {
            return "translucent";
        }
        if (renderType == RenderType.tripwire()) {
            return "tripwire";
        }
        return "non_canonical";
    }

    private record PartialEvidence(PartialModel partial, String source) {
    }
}
