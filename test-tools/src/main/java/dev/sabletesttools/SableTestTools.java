package dev.sabletesttools;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SableTestTools.MOD_ID)
public final class SableTestTools {
    public static final String MOD_ID = "sable_test_tools";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    static final RegistryObject<Item> WAND = ITEMS.register("selection_wand",
            () -> new SelectionWandItem(new Item.Properties().stacksTo(1)));
    static final RegistryObject<Item> GRAVITY_GUN = ITEMS.register("gravity_gun",
            () -> new GravityGunItem(new Item.Properties().stacksTo(1)));

    public SableTestTools() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        TestToolsNetwork.register();
        modBus.addListener(this::addCreativeTabContents);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreativeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WAND);
            event.accept(GRAVITY_GUN);
        }
    }

    @SubscribeEvent
    public void registerCommands(final RegisterCommandsEvent event) {
        SableTestCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void selectFirstPoint(final PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof final ServerPlayer player)
                || event.getItemStack().getItem() != WAND.get()) {
            return;
        }
        TestSessions.get(player).select(player.serverLevel().dimension(), event.getPos(), true);
        player.sendSystemMessage(Component.literal("[Sable Test] 点 1 = "
                + SelectionWandItem.format(event.getPos())));
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void selectSecondPoint(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof final ServerPlayer player)
                || event.getItemStack().getItem() != WAND.get()) {
            return;
        }
        TestSessions.get(player).select(player.serverLevel().dimension(), event.getPos(), false);
        player.sendSystemMessage(Component.literal("[Sable Test] 点 2 = "
                + SelectionWandItem.format(event.getPos())));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void playerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            GravityGunController.release(player, false);
        }
        TestSessions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void playerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof final ServerPlayer player) {
            GravityGunController.tick(player);
        }
    }

    static ItemStack wandStack() {
        return new ItemStack(WAND.get());
    }

    static ItemStack gravityGunStack() {
        return new ItemStack(GRAVITY_GUN.get());
    }
}
