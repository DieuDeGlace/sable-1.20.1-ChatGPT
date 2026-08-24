package dev.ryanhcode.sable.validation;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;

import java.util.UUID;

/** Observes the production freeze bridge without adding probe calls to production code. */
@Mod.EventBusSubscriber(modid = SableForgeValidationMod.MOD_ID, value = Dist.CLIENT)
public final class SableForgeFreezeValidationEvents {
    private static UUID pendingTarget;
    private static Vector3d pendingAnchor;
    private static ClientSubLevel pendingSubLevel;

    private SableForgeFreezeValidationEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeClientTick(final TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("sable.freezeValidation") || event.phase != TickEvent.Phase.START) return;
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        final PlayerFreezeExtension freeze = (PlayerFreezeExtension) player;
        final UUID targetId = freeze.sable$getFrozenToSubLevel();
        if (targetId == null) return;
        final SubLevelContainer container = SubLevelContainer.getContainer(player.level());
        final ClientSubLevel target = container == null ? null : (ClientSubLevel) container.getSubLevel(targetId);
        if (target != null && target.isFinalized()) {
            pendingTarget = targetId;
            pendingAnchor = new Vector3d(freeze.sable$getFrozenToSubLevelAnchor());
            pendingSubLevel = target;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterClientTick(final TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("sable.freezeValidation") || event.phase != TickEvent.Phase.START
                || pendingTarget == null) return;
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        final PlayerFreezeExtension freeze = (PlayerFreezeExtension) player;
        if (freeze.sable$getFrozenToSubLevel() == null) {
            SableForgeFreezeValidationProbe.record(player, pendingSubLevel, pendingTarget, pendingAnchor);
            pendingTarget = null;
            pendingAnchor = null;
            pendingSubLevel = null;
        }
    }
}
