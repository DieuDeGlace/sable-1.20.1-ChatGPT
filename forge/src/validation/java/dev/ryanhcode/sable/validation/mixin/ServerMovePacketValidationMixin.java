package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.validation.SableForgeMovementValidationProbe;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerboundMovePlayerPacket.class, priority = 900)
public abstract class ServerMovePacketValidationMixin {
    @Mutable @Shadow @Final protected double x;
    @Mutable @Shadow @Final protected double y;
    @Mutable @Shadow @Final protected double z;
    @Unique private Vec3 sable$validationLocal;
    @Unique private Vec3 sable$validationExpected;

    @Inject(method = "sable$handle", at = @At("HEAD"), remap = false)
    private void sable$prepareServerPacket(final ServerPlayer player, final CallbackInfo ci) {
        if (!Boolean.getBoolean("sable.movementValidation")) return;
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), this.x, this.z);
        if (subLevel != null) {
            this.sable$validationLocal = new Vec3(this.x, this.y, this.z);
            final Vector3d expected = subLevel.logicalPose().transformPosition(new Vector3d(this.x, this.y, this.z));
            this.sable$validationExpected = new Vec3(expected.x, expected.y, expected.z);
        }
    }

    @Inject(method = "sable$handle", at = @At("RETURN"), remap = false)
    private void sable$finishServerPacket(final ServerPlayer player, final CallbackInfo ci) {
        if (this.sable$validationLocal != null) {
            SableForgeMovementValidationProbe.serverPacket(this.sable$validationLocal,
                    new Vec3(this.x, this.y, this.z), this.sable$validationExpected);
        }
        this.sable$validationLocal = null;
        this.sable$validationExpected = null;
    }
}
