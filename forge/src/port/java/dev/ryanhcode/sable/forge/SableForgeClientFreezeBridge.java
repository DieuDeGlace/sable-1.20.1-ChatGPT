package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;

import java.util.UUID;

/**
 * Finishes queued client freeze teleports from the stable Forge client tick.
 *
 * <p>Forge 47's client level only invokes {@link LocalPlayer#tick()} while the
 * player remains in its ticking-entity set. A freeze packet can arrive after
 * that lifecycle has stopped, so the upstream LocalPlayer redirect alone is
 * not a reliable consumer on 1.20.1.</p>
 */
public final class SableForgeClientFreezeBridge {
    private SableForgeClientFreezeBridge() {
    }

    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        final PlayerFreezeExtension freeze = (PlayerFreezeExtension) player;
        final UUID targetId = freeze.sable$getFrozenToSubLevel();
        if (targetId == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(player.level());
        final ClientSubLevel target = container == null ? null : (ClientSubLevel) container.getSubLevel(targetId);
        if (target == null || !target.isFinalized()) {
            return;
        }
        freeze.sable$teleport();
        freeze.sable$freezeTo(null, null);
    }
}
