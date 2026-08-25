package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.backend.engine.AbstractInstancer;
import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.InstancerKey;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;
import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelModelDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Records why target Flywheel models do or do not pass backend admission. */
@Mixin(value = DrawManager.class, remap = false)
public class DrawManagerMixin {
    @Unique
    private static final AtomicInteger sable$MODEL_TRACES = new AtomicInteger();

    @Inject(
            method = "getInstancer(Ldev/engine_room/flywheel/backend/engine/InstancerKey;)Ldev/engine_room/flywheel/backend/engine/AbstractInstancer;",
            at = @At("HEAD")
    )
    private void sable$traceEmbeddedModelAdmission(final InstancerKey<?> key,
                                                    final CallbackInfoReturnable<AbstractInstancer<?>> cir) {
        if (!(key.environment() instanceof EmbeddedEnvironment)) {
            return;
        }

        final int trace = sable$MODEL_TRACES.getAndIncrement();
        if (trace >= 96) {
            return;
        }

        final Model model = key.model();
        final List<Model.ConfiguredMesh> meshes = model.meshes();
        if (meshes.isEmpty()) {
            SableFlywheelModelDiagnostics.traceEmptyAdmission(model);
        }
        int firstInvalidMaterial = -1;
        for (int i = 0; i < meshes.size(); i++) {
            if (!MaterialRenderState.materialIsAllNonNull(meshes.get(i).material())) {
                firstInvalidMaterial = i;
                break;
            }
        }

        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-FLYWHEEL-MODEL-ADMISSION: model={}, meshes={}, firstInvalidMaterial={}, "
                        + "environment={}, instanceType={}, bias={}",
                model.getClass().getName(), meshes.size(), firstInvalidMaterial,
                key.environment().getClass().getName(), key.type().getClass().getName(), key.bias());
    }
}
