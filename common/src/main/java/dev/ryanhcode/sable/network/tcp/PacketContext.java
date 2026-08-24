package dev.ryanhcode.sable.network.tcp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface PacketContext {
    Level level();

    Player player();
}
