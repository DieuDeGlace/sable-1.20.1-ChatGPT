package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelModelDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/** Traces the exact baked-model inputs that produce an empty Flywheel partial model. */
@Mixin(value = Models.class, remap = false)
public class ModelsMixin {
    @Inject(
            method = "partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;)Ldev/engine_room/flywheel/api/model/Model;",
            at = @At("RETURN")
    )
    private static void sable$recordPartialModel(final PartialModel partial,
                                                 final CallbackInfoReturnable<Model> cir) {
        SableFlywheelModelDiagnostics.recordPartial(partial, cir.getReturnValue(), "single");
    }

    @Inject(
            method = "partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Ljava/lang/Object;Ljava/util/function/BiConsumer;)Ldev/engine_room/flywheel/api/model/Model;",
            at = @At("RETURN")
    )
    private static void sable$recordTransformedPartialModel(final PartialModel partial,
                                                            final Object key,
                                                            final BiConsumer<Object, PoseStack> transformer,
                                                            final CallbackInfoReturnable<Model> cir) {
        SableFlywheelModelDiagnostics.recordPartial(partial, cir.getReturnValue(), "transformed");
    }
}
