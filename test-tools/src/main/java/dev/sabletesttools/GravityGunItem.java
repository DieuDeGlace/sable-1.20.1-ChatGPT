package dev.sabletesttools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

final class GravityGunItem extends Item {
    GravityGunItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        if (player instanceof final ServerPlayer serverPlayer) {
            GravityGunController.use(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getPlayer() instanceof final ServerPlayer player) {
            GravityGunController.use(player);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Level level, final List<Component> tooltip,
                                final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sable_test_tools.gravity_gun.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sable_test_tools.gravity_gun.tooltip_alt").withStyle(ChatFormatting.DARK_GRAY));
    }
}
