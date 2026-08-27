package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import dev.ryanhcode.sable.forge.client.SableFlywheelBackendController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BackendManagerImpl.class, remap = false)
public class BackendManagerDebugOverrideMixin {
    @Shadow
    private static Backend backend;

    @Inject(method = "chooseBackend", at = @At("HEAD"), cancellable = true)
    private static void sable$applyDebugBackendOverride(final CallbackInfo ci) {
        final Backend forced = SableFlywheelBackendController.forcedBackendOrNull();
        if (forced == null) {
            return;
        }
        backend = forced;
        ci.cancel();
    }
}
