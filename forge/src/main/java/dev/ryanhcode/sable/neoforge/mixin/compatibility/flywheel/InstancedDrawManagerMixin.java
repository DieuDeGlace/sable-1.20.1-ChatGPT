package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDraw;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDrawManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelDiagnostics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = InstancedDrawManager.class, remap = false)
public class InstancedDrawManagerMixin {
    private static final AtomicInteger sable$RENDER_TRACES = new AtomicInteger();

    @Shadow
    @Final
    private List<InstancedDraw> allDraws;

    @Shadow
    @Final
    private List<InstancedDraw> draws;

    @Shadow
    @Final
    private List<InstancedDraw> oitDraws;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0))
    private void sable$traceEmbeddedDrawAdmission(final LightStorage lightStorage,
                                                  final EnvironmentStorage environmentStorage,
                                                  final CallbackInfo ci) {
        final int created = SableFlywheelDiagnostics.embeddedVisualsCreated();
        if (created == 0) {
            return;
        }

        final int trace = sable$RENDER_TRACES.getAndIncrement();
        if (trace >= 160 || trace % 10 != 0) {
            return;
        }

        int embeddedDraws = 0;
        int embeddedInstances = 0;
        int globalInstances = 0;
        for (final InstancedDraw draw : this.allDraws) {
            final int instances = ((InstancedDrawAccessor) draw).sable$getInstancer().instanceCount();
            if (draw.groupKey.environment() instanceof EmbeddedEnvironment) {
                embeddedDraws++;
                embeddedInstances += instances;
            } else {
                globalInstances += instances;
            }
        }

        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-INSTANCED-ADMISSION: createdEmbeddedVisuals={}, allDraws={}, "
                        + "directDraws={}, oitDraws={}, embeddedDraws={}, embeddedInstances={}, globalInstances={}",
                created, this.allDraws.size(), this.draws.size(), this.oitDraws.size(), embeddedDraws,
                embeddedInstances, globalInstances);
    }
}
