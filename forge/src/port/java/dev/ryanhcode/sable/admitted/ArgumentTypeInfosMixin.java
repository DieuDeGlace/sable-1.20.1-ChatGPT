package dev.ryanhcode.sable.mixin.command;

import com.mojang.brigadier.arguments.ArgumentType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.command.Vec3ArgumentAbsolute;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Upstream registration hook, admitted after verifying the 1.20.1 target bytecode. */
@Mixin(ArgumentTypeInfos.class)
public abstract class ArgumentTypeInfosMixin {
    @Shadow
    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>>
    ArgumentTypeInfo<A, T> register(final Registry<ArgumentTypeInfo<?, ?>> registry,
                                    final String name,
                                    final Class<? extends A> argumentClass,
                                    final ArgumentTypeInfo<A, T> info) {
        return null;
    }

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void sable$bootstrap(final Registry<ArgumentTypeInfo<?, ?>> registry,
                                        final CallbackInfoReturnable<ArgumentTypeInfo<?, ?>> cir) {
        register(registry, Sable.MOD_ID + ":sub_level",
                SubLevelArgumentType.class, new SubLevelArgumentType.Info());
        register(registry, Sable.MOD_ID + ":vec3_absolute",
                Vec3ArgumentAbsolute.class, SingletonArgumentInfo.contextFree(Vec3ArgumentAbsolute::vec3));
    }
}
