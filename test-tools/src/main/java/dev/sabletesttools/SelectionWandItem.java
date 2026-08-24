package dev.sabletesttools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class SelectionWandItem extends Item {
    SelectionWandItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof final ServerPlayer player) {
            TestSessions.get(player).select(player.serverLevel().dimension(), context.getClickedPos(), false);
            player.sendSystemMessage(Component.literal("[Sable Test] 点 2 = " + format(context.getClickedPos()))
                    .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final Level level,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sable_test_tools.selection_wand.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("/sabletest status | /sabletest assemble")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    static String format(final net.minecraft.core.BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
