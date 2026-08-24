package dev.ryanhcode.sable.validation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Development-only mod that owns all executable port validation fixtures. */
@Mod(SableForgeValidationMod.MOD_ID)
public final class SableForgeValidationMod {
    public static final String MOD_ID = "sable_validation";

    public SableForgeValidationMod() {
        SableForgeLifecycleValidation.register(FMLJavaModLoadingContext.get().getModEventBus());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    ClientAudioStreamValidation::onClientSetup);
        }
    }
}
