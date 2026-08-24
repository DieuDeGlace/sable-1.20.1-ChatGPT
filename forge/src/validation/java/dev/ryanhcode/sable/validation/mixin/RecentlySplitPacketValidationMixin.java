package dev.ryanhcode.sable.validation.mixin;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.validation.SableForgeNetworkValidationTelemetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

import java.util.List;

/** Captures transient history immediately after the production split packet has applied it. */
@Mixin(value = ClientboundRecentlySplitSubLevelPacket.class, remap = false)
public abstract class RecentlySplitPacketValidationMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private void sable$recordSplitInputs(final PacketContext context, final CallbackInfo ci) {
        final ClientboundRecentlySplitSubLevelPacket packet =
                (ClientboundRecentlySplitSubLevelPacket) (Object) this;
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        final SubLevel child = container.getSubLevel(packet.splitSubLevelID());
        final SubLevel origin = container.getSubLevel(packet.splitFromID());
        LOGGER.info("Sable packet-12 input history: child={}, origin={}, child ticks={}, origin ticks={}, "
                        + "interpolation pointer={}, stopped={}",
                packet.splitSubLevelID(), packet.splitFromID(), snapshotTicks(child), snapshotTicks(origin),
                container instanceof ClientSubLevelContainer clientContainer
                        ? clientContainer.getInterpolation().getTickPointer() : null,
                container instanceof ClientSubLevelContainer clientContainer
                        ? clientContainer.getInterpolation().isStopped() : null);
    }

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void sable$recordSplitHistory(final PacketContext context, final CallbackInfo ci) {
        final ClientboundRecentlySplitSubLevelPacket packet =
                (ClientboundRecentlySplitSubLevelPacket) (Object) this;
        final SubLevel subLevel = SubLevelContainer.getContainer(context.level())
                .getSubLevel(packet.splitSubLevelID());
        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            SableForgeNetworkValidationTelemetry.recordSplitInterpolationHistory(clientSubLevel);
            LOGGER.info("Sable packet-12 output history: child={}, ticks={}",
                    packet.splitSubLevelID(), snapshotTicks(clientSubLevel));
        }
    }

    private static List<Integer> snapshotTicks(final SubLevel subLevel) {
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
            return List.of();
        }
        return clientSubLevel.getInterpolator().buffer.stream()
                .map(snapshot -> snapshot.gameTick()).toList();
    }
}
