package dev.ryanhcode.sable.forge;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.network.codec.StreamCodec;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundDimensionPhysicsPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFloatingBlockMaterialPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundGizmoMoveSubLevelPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeBoundsSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeSubLevelNamePacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundEnterGizmoPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundSableUDPActivationPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SablePacketManager;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.physics.config.block_properties.BlockStateConditionSet;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Quaterniond;

@Mod(SableForgeBootstrap.MOD_ID)
public final class SableForgeBootstrap {
    public static final String MOD_ID = "sable";
    public static final String SOURCE_BASELINE = "6966d2928340de7631abcecf8549904b877df0a8";
    public static final String CREATE_BASELINE = "1a1a9a2819b4f89f78caec41b55ed8cb222fa24b";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> BUNDLED_PHYSICS_DEFINITION_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bedrock_absurdity"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bouncy"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "end_stone_brick_slab"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "end_stones"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "flywheel"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "fragile"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "grindstone"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "half_volume"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "heavy"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "light"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "mud"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "normal"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "piston"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "quarter_volume"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slippery"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "sticky"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "super_heavy"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "super_light"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "weightless")
    );
    private final SablePacketManager packetManager;
    private final RegistryObject<Attribute> punchStrength;
    private final RegistryObject<Attribute> punchCooldown;

    public SableForgeBootstrap() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::verifyReloadedPhysicsDefinitions);
        MinecraftForge.EVENT_BUS.addListener(this::syncDataPack);
        final DeferredRegister<Attribute> attributes = DeferredRegister.create(Registries.ATTRIBUTE, MOD_ID);
        this.punchStrength = attributes.register(SableAttributes.PUNCH_STRENGTH_NAME,
                () -> SableAttributes.PUNCH_STRENGTH_ATTRIBUTE);
        this.punchCooldown = attributes.register(SableAttributes.PUNCH_COOLDOWN_NAME,
                () -> SableAttributes.PUNCH_COOLDOWN_ATTRIBUTE);
        attributes.register(modEventBus);
        Sable.init();
        final SableLoaderPlatform loaderPlatform = SableLoaderPlatform.INSTANCE;
        final String forgeVersion = loaderPlatform.getModVersion("forge");
        final Path gameDirectory = loaderPlatform.getGameDirectory();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SableConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SableServerConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(SableForgeClientFreezeBridge::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(
                    dev.ryanhcode.sable.forge.client.SableFlywheelDebugCommands::register);
            MinecraftForge.EVENT_BUS.addListener(
                    dev.ryanhcode.sable.forge.client.SableIrisProgramRecoveryCommands::register);
            SableClient.init();
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SableClientConfig.SPEC);
            modEventBus.<ModConfigEvent.Loading>addListener(event -> {
                if (event.getConfig().getSpec() == SableClientConfig.SPEC) {
                    SableClientConfig.onUpdate(false);
                }
            });
            modEventBus.<ModConfigEvent.Reloading>addListener(event -> {
                if (event.getConfig().getSpec() == SableClientConfig.SPEC) {
                    SableClientConfig.onUpdate(true);
                }
            });
        }
        LOGGER.info("Sable Forge 1.20.1 CP2 bootstrap loaded (Sable {}, Create {})", SOURCE_BASELINE, CREATE_BASELINE);
        LOGGER.info("Sable loader platform service verified (Forge {}, game directory {})", forgeVersion, gameDirectory);
        LOGGER.info("Sable platform services verified ({}, {}, null wrapped level={})",
                SableLoaderPlatform.INSTANCE.getClass().getName(),
                SableAssemblyPlatform.INSTANCE.getClass().getName(),
                SablePlatform.INSTANCE.isWrappedLevel(null));
        LOGGER.info("Sable chunk event platform service verified ({})",
                SableChunkEventPlatform.INSTANCE.getClass().getName());
        LOGGER.info("Sable plot persistence platform service verified ({})",
                SablePlotPlatform.INSTANCE.getClass().getName());
        LOGGER.info("Sable event platform services verified ({}, {})",
                SableEventPlatform.INSTANCE.getClass().getName(),
                SableEventPublishPlatform.INSTANCE.getClass().getName());
        SableEventPlatform.INSTANCE.onSubLevelContainerReady(this::verifySubLevelContainerReady);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LOGGER.info("Sable client render platform service verified ({})",
                    SableSubLevelRenderPlatform.INSTANCE.getClass().getName());
        }
        LOGGER.info("Sable common and server ForgeConfigSpec instances registered");
        LOGGER.info("Sable Companion service verified ({})",
                SableCompanion.INSTANCE.getClass().getName());
        this.packetManager = SableTCPPackets.manager();
        verifyNetworkEnvelope();
        verifyCurrentProductionPacketSet();
        if (!(Sable.HELPER instanceof dev.ryanhcode.sable.ActiveSableCompanion)) {
            throw new IllegalStateException("ActiveSableCompanion was not selected: " + Sable.HELPER.getClass());
        }
        if (!(PhysicsPipelineProvider.INSTANCE instanceof dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipelineProvider)) {
            throw new IllegalStateException("Rapier physics provider was not selected: " + PhysicsPipelineProvider.INSTANCE.getClass());
        }
        LOGGER.info("Sable active companion and Rapier provider verified ({}, {})",
                Sable.HELPER.getClass().getName(), PhysicsPipelineProvider.INSTANCE.getClass().getName());
        Rapier3D.verifyNativeSceneStep();
    }

    private void verifySubLevelContainerReady(final net.minecraft.world.level.Level level,
                                              final SubLevelContainer container) {
        if (level.isClientSide) {
            if (!(container instanceof ClientSubLevelContainer)) {
                throw new IllegalStateException("Client level received non-client Sable container: "
                        + container.getClass().getName());
            }
        } else {
            if (!(container instanceof final ServerSubLevelContainer serverContainer)) {
                throw new IllegalStateException("Server level received non-server Sable container: "
                        + container.getClass().getName());
            }
            serverContainer.physicsSystem();
            serverContainer.trackingSystem();
        }
        LOGGER.info("Sable sublevel container ready ({}, {}, side={})",
                level.dimension().location(), container.getClass().getName(),
                level.isClientSide ? "client" : "server");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            this.initializeAndVerifyPlayerAttributes();
            this.verifyTagKeys();
            this.verifyPhysicsBlockDefinitionCodec();
        });
    }

    private void initializeAndVerifyPlayerAttributes() {
        SableAttributes.PUNCH_STRENGTH = this.punchStrength.get();
        SableAttributes.PUNCH_COOLDOWN = this.punchCooldown.get();
        SableAttributes.register();

        final ResourceLocation strengthId = BuiltInRegistries.ATTRIBUTE.getKey(SableAttributes.PUNCH_STRENGTH);
        final ResourceLocation cooldownId = BuiltInRegistries.ATTRIBUTE.getKey(SableAttributes.PUNCH_COOLDOWN);
        final ResourceLocation expectedStrengthId = ResourceLocation.fromNamespaceAndPath(
                MOD_ID, SableAttributes.PUNCH_STRENGTH_NAME);
        final ResourceLocation expectedCooldownId = ResourceLocation.fromNamespaceAndPath(
                MOD_ID, SableAttributes.PUNCH_COOLDOWN_NAME);
        final RangedAttribute strength = (RangedAttribute) SableAttributes.PUNCH_STRENGTH;
        final RangedAttribute cooldown = (RangedAttribute) SableAttributes.PUNCH_COOLDOWN;
        if (!expectedStrengthId.equals(strengthId) || !expectedCooldownId.equals(cooldownId)
                || strength.getDefaultValue() != 1.0D || strength.getMinValue() != -100.0D
                || strength.getMaxValue() != 100.0D || !strength.isClientSyncable()
                || cooldown.getDefaultValue() != 0.0D || cooldown.getMinValue() != 0.0D
                || cooldown.getMaxValue() != 10.0D || !cooldown.isClientSyncable()) {
            throw new IllegalStateException("Sable player attribute registry does not match the source baseline");
        }
        if (!DefaultAttributes.getSupplier(EntityType.PLAYER).hasAttribute(SableAttributes.PUNCH_STRENGTH)
                || !DefaultAttributes.getSupplier(EntityType.PLAYER).hasAttribute(SableAttributes.PUNCH_COOLDOWN)) {
            throw new IllegalStateException("Sable player attributes were not added to the player supplier");
        }
        LOGGER.info("Sable player attributes verified ({}, {})", strengthId, cooldownId);
    }

    private void registerReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(PhysicsBlockPropertiesDefinitionLoader.INSTANCE);
        event.addListener(DimensionPhysicsData.ReloadListener.INSTANCE);
        event.addListener(FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE);
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        SableCommand.register(event.getDispatcher(), event.getBuildContext());
        LOGGER.info("Sable command tree registered");
    }

    private void verifyTagKeys() {
        final Map<TagKey<?>, ResourceLocation> expected = Map.of(
                SableTags.RETAIN_IN_SUB_LEVEL, ResourceLocation.fromNamespaceAndPath(MOD_ID, "retain_in_sub_level"),
                SableTags.DESTROY_WITH_SUB_LEVEL, ResourceLocation.fromNamespaceAndPath(MOD_ID, "destroy_with_sub_level"),
                SableTags.DESTROY_WHEN_LEAVING_PLOT, ResourceLocation.fromNamespaceAndPath(MOD_ID, "destroy_when_leaving_plot"),
                SableTags.ALWAYS_CHUNK_RENDERING, ResourceLocation.fromNamespaceAndPath(MOD_ID, "always_chunk_rendering"),
                SableTags.BOUNCY, ResourceLocation.fromNamespaceAndPath(MOD_ID, "bouncy"),
                SableTags.PADDLES, ResourceLocation.fromNamespaceAndPath(MOD_ID, "paddles"),
                SableTags.SILENT_ASSEMBLY_REMOVAL, ResourceLocation.fromNamespaceAndPath(MOD_ID, "silent_assembly_removal")
        );
        expected.forEach((tag, id) -> {
            if (!tag.location().equals(id)) {
                throw new IllegalStateException("Sable tag key does not match the source baseline: " + tag);
            }
        });
        LOGGER.info("Sable tag keys verified (7 exact entries)");
    }

    public static void verifyLoadedTagContents() {
        final ResourceLocation contraptionId = ResourceLocation.fromNamespaceAndPath("create", "contraption");
        final ResourceLocation superGlueId = ResourceLocation.fromNamespaceAndPath("create", "super_glue");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(contraptionId)
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(superGlueId)) {
            throw new IllegalStateException("Exact Create 6.0.8 entity types required by Sable tags are missing");
        }
        final EntityType<?> contraption = BuiltInRegistries.ENTITY_TYPE.get(contraptionId);
        final EntityType<?> superGlue = BuiltInRegistries.ENTITY_TYPE.get(superGlueId);
        if (!Blocks.SLIME_BLOCK.defaultBlockState().is(SableTags.BOUNCY)
                || !new ItemStack(Items.IRON_SHOVEL).is(SableTags.PADDLES)
                || !EntityType.MINECART.is(SableTags.RETAIN_IN_SUB_LEVEL)
                || !EntityType.MINECART.is(SableTags.DESTROY_WHEN_LEAVING_PLOT)
                || !EntityType.PAINTING.is(SableTags.RETAIN_IN_SUB_LEVEL)
                || !contraption.is(SableTags.RETAIN_IN_SUB_LEVEL)
                || !contraption.is(SableTags.DESTROY_WITH_SUB_LEVEL)
                || !superGlue.is(SableTags.RETAIN_IN_SUB_LEVEL)
                || !superGlue.is(SableTags.DESTROY_WITH_SUB_LEVEL)) {
            throw new IllegalStateException("Loaded Sable tag contents do not match bundled target resources");
        }
    }

    private void verifyReloadedPhysicsDefinitions(final ServerStartedEvent event) {
        verifyLoadedTagContents();
        final List<PhysicsBlockPropertiesDefinition> definitions = List.copyOf(
                PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions());
        final Set<ResourceLocation> loadedDefinitionIds =
                PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitionIds();
        if (!loadedDefinitionIds.containsAll(BUNDLED_PHYSICS_DEFINITION_IDS)) {
            final Set<ResourceLocation> missingDefinitionIds = new java.util.LinkedHashSet<>(
                    BUNDLED_PHYSICS_DEFINITION_IDS);
            missingDefinitionIds.removeAll(loadedDefinitionIds);
            throw new IllegalStateException("Missing bundled Sable physics block definitions: "
                    + missingDefinitionIds);
        }
        for (int index = 1; index < definitions.size(); index++) {
            if (definitions.get(index - 1).priority() > definitions.get(index).priority()) {
                throw new IllegalStateException("Physics block definitions are not sorted by ascending priority");
            }
        }
        for (final PhysicsBlockPropertiesDefinition definition : definitions) {
            final net.minecraft.util.ExtraCodecs.TagOrElementLocation selector = definition.selector();
            if (selector.tag()) {
                final TagKey<Block> tag = TagKey.create(Registries.BLOCK, selector.id());
                if (BuiltInRegistries.BLOCK.getTag(tag).isEmpty()) {
                    throw new IllegalStateException("Bundled physics definition references unknown block tag "
                            + selector.id());
                }
            } else if (!BuiltInRegistries.BLOCK.containsKey(selector.id())) {
                throw new IllegalStateException("Bundled physics definition references unknown block "
                        + selector.id());
            }
        }
        final ResourceLocation bundledFloatingMaterial =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "end_stone");
        if (!FloatingBlockMaterialDataHandler.allMaterials.containsKey(bundledFloatingMaterial)) {
            throw new IllegalStateException("Missing bundled Sable floating material: "
                    + bundledFloatingMaterial);
        }
        PhysicsBlockPropertiesDefinitionLoader.INSTANCE.applyAll();
        final BlockStateExtension bedrock = (BlockStateExtension) (Object) Blocks.BEDROCK.defaultBlockState();
        final BlockStateExtension grindstone = (BlockStateExtension) (Object) Blocks.GRINDSTONE.defaultBlockState();
        final BlockStateExtension slime = (BlockStateExtension) (Object) Blocks.SLIME_BLOCK.defaultBlockState();
        final BlockStateExtension pistonExtended = (BlockStateExtension) (Object) Blocks.PISTON.defaultBlockState()
                .setValue(BlockStateProperties.EXTENDED, true);
        final double bedrockMass = bedrock.sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
        final double grindstoneFriction = grindstone.sable$getProperty(PhysicsBlockPropertyTypes.FRICTION.get());
        final double slimeMass = slime.sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
        final double slimeRestitution = slime.sable$getProperty(PhysicsBlockPropertyTypes.RESTITUTION.get());
        final double pistonExtendedMass = pistonExtended.sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
        if (Double.compare(bedrockMass, 1000.0D) != 0) {
            throw new IllegalStateException("Expected bundled bedrock mass 1000.0, found " + bedrockMass);
        }
        if (Double.compare(grindstoneFriction, 0.05D) != 0) {
            throw new IllegalStateException("Expected bundled grindstone friction 0.05, found "
                    + grindstoneFriction);
        }
        if (Double.compare(slimeRestitution, 0.5D) != 0) {
            throw new IllegalStateException("Expected tag-applied slime restitution 0.5, found "
                    + slimeRestitution);
        }
        if (Double.compare(slimeMass, 1.0D) != 0) {
            throw new IllegalStateException("Expected tag-removal-preserved slime mass default 1.0, found "
                    + slimeMass);
        }
        if (Double.compare(pistonExtendedMass, 0.5D) != 0) {
            throw new IllegalStateException("Expected override-applied extended piston mass 0.5, found "
                    + pistonExtendedMass);
        }
        LOGGER.info("Sable server data reload verified ({} required bundled definitions present, {} total "
                        + "physics definitions, {} floating materials)",
                BUNDLED_PHYSICS_DEFINITION_IDS.size(), definitions.size(),
                FloatingBlockMaterialDataHandler.allMaterials.size());
        LOGGER.info("Sable loaded tag contents verified (vanilla and Create members)");
        LOGGER.info("Sable BlockState physics application verified (bedrock mass {}, grindstone friction {}, "
                        + "slime mass/restitution {}/{}, extended piston mass {})",
                bedrockMass, grindstoneFriction, slimeMass, slimeRestitution, pistonExtendedMass);
    }

    private void syncDataPack(final OnDatapackSyncEvent event) {
        final List<PhysicsBlockPropertiesDefinition> definitions = List.copyOf(
                PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions());
        final ClientboundDimensionPhysicsPacket dimensionPacket = DimensionPhysicsData.compilePacket();
        for (final net.minecraft.server.level.ServerPlayer player : event.getPlayers()) {
            final SablePacketManager.PacketSink sink = SablePacketManager.player(player);
            definitions.forEach(definition -> sink.sendPacket(new ClientboundPhysicsPropertyPacket(definition)));
            sink.sendPacket(dimensionPacket);
            FloatingBlockMaterialDataHandler.allMaterials.forEach((name, material) ->
                    sink.sendPacket(new ClientboundFloatingBlockMaterialPacket(name, material)));
        }
        LOGGER.info("Sable staged datapack sync sent to {} player(s): {} physics definitions, {} dimension "
                        + "entries, {} floating materials",
                event.getPlayers().size(), definitions.size(), dimensionPacket.dimensionPhysics().size(),
                FloatingBlockMaterialDataHandler.allMaterials.size());
    }

    private void registerStagedProductionPackets() {
        this.packetManager.registerClientbound(9, ClientboundPhysicsPropertyPacket.TYPE,
                ClientboundPhysicsPropertyPacket.CODEC, ClientboundPhysicsPropertyPacket::handle);
        this.packetManager.registerClientbound(10, ClientboundFloatingBlockMaterialPacket.TYPE,
                ClientboundFloatingBlockMaterialPacket.CODEC, ClientboundFloatingBlockMaterialPacket::handle);
        this.packetManager.registerClientbound(11, ClientboundDimensionPhysicsPacket.TYPE,
                ClientboundDimensionPhysicsPacket.CODEC, ClientboundDimensionPhysicsPacket::handle);
    }

    private void verifyPhysicsBlockDefinitionCodec() {
        verifyForceGroups();
        if (PhysicsBlockPropertyTypes.count() != 8) {
            throw new IllegalStateException("Expected 8 registered physics block property types, found "
                    + PhysicsBlockPropertyTypes.count());
        }
        final BlockStateExtension unappliedBedrock =
                (BlockStateExtension) (Object) Blocks.BEDROCK.defaultBlockState();
        final double defaultMass = unappliedBedrock.sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
        if (Double.compare(defaultMass, 1.0D) != 0) {
            throw new IllegalStateException("Expected unapplied BlockState mass default 1.0, found " + defaultMass);
        }

        final Map<ResourceLocation, Object> properties = new LinkedHashMap<>();
        properties.put(PhysicsBlockPropertyTypes.MASS.getId(), 3.5D);
        properties.put(PhysicsBlockPropertyTypes.FRAGILE.getId(), true);
        final Map<ResourceLocation, Object> overrideProperties = new LinkedHashMap<>();
        overrideProperties.put(PhysicsBlockPropertyTypes.FRICTION.getId(), 0.25D);
        final BlockStateConditionSet condition = new BlockStateConditionSet(List.of(
                new BlockStateConditionSet.BlockStateCondition("axis", "y")));
        final PhysicsBlockPropertiesDefinition expected = new PhysicsBlockPropertiesDefinition(
                new net.minecraft.util.ExtraCodecs.TagOrElementLocation(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), false),
                17,
                properties,
                Optional.of(Map.of(condition, overrideProperties)));
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PhysicsBlockPropertiesDefinition.STREAM_CODEC.encode(buffer, expected);
        final PhysicsBlockPropertiesDefinition decoded = PhysicsBlockPropertiesDefinition.STREAM_CODEC.decode(buffer);

        if (!expected.equals(decoded) || buffer.readableBytes() != 0) {
            throw new IllegalStateException("Physics block definition codec round trip failed: " + decoded);
        }

        final ClientboundPhysicsPropertyPacket propertyPacket = new ClientboundPhysicsPropertyPacket(expected);
        if (!propertyPacket.equals(roundTrip(ClientboundPhysicsPropertyPacket.CODEC, propertyPacket))) {
            throw new IllegalStateException("Physics property packet codec round trip failed");
        }
        final ClientboundFloatingBlockMaterialPacket materialPacket = new ClientboundFloatingBlockMaterialPacket(
                ResourceLocation.fromNamespaceAndPath("sable", "test_material"),
                new FloatingBlockMaterial(true, false, true, 8.5, 0.25, 0.1, 0.2, 0.3, 0.4));
        if (!materialPacket.equals(roundTrip(ClientboundFloatingBlockMaterialPacket.CODEC, materialPacket))) {
            throw new IllegalStateException("Floating material packet codec round trip failed");
        }
        final DimensionPhysics dimensionPhysics = new DimensionPhysics(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), 100,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
        final ClientboundDimensionPhysicsPacket dimensionPacket = new ClientboundDimensionPhysicsPacket(
                List.of(dimensionPhysics));
        if (!dimensionPacket.equals(roundTrip(ClientboundDimensionPhysicsPacket.CODEC, dimensionPacket))) {
            throw new IllegalStateException("Dimension physics packet codec round trip failed");
        }
        verifyPacketCodecAndDiscriminator(ClientboundPhysicsPropertyPacket.CODEC, propertyPacket, 9);
        verifyPacketCodecAndDiscriminator(ClientboundFloatingBlockMaterialPacket.CODEC, materialPacket, 10);
        verifyPacketCodecAndDiscriminator(ClientboundDimensionPhysicsPacket.CODEC, dimensionPacket, 11);

        LOGGER.info("Sable BlockState Mixin default and physics block registry, definition codec and three "
                        + "production packet codecs verified (default mass {}, {} property types)",
                defaultMass, PhysicsBlockPropertyTypes.count());
        LOGGER.info("Sable full production TCP set verified "
                + "(17/17 exact codec byte round trips and discriminators 0-16)");
    }

    private static void verifyForceGroups() {
        final Set<ResourceLocation> expectedIds = Set.of(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "gravity"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "drag"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "levitation"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "balloon_lift"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "propulsion"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "lift"),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "magnetic_force"));
        if (ForceGroups.count() != expectedIds.size()
                || !ForceGroups.REGISTRY.keySet().equals(expectedIds)) {
            throw new IllegalStateException("Sable force-group registry mismatch: "
                    + ForceGroups.REGISTRY.keySet());
        }
        final ForceGroup gravity = ForceGroups.GRAVITY.get();
        final ForceGroup levitation = ForceGroups.LEVITATION.get();
        if (gravity.color() != 0x216e55 || gravity.defaultDisplayed()
                || levitation.color() != 0x734480 || !levitation.defaultDisplayed()) {
            throw new IllegalStateException("Sable force-group fields do not match the source baseline");
        }
        LOGGER.info("Sable force-group registry verified ({} exact entries)", ForceGroups.count());
    }

    private static <P> P roundTrip(final StreamCodec<ByteBuf, P> codec, final P expected) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, expected);
        final P decoded = codec.decode(buffer);
        if (buffer.readableBytes() != 0) {
            throw new IllegalStateException("Packet codec left " + buffer.readableBytes() + " unread bytes");
        }
        return decoded;
    }

    private static void verifyNetworkEnvelope() {
        final Packet<?> envelope = SablePacketManager.clientboundVanillaPacket(
                new ClientboundDimensionPhysicsPacket(List.of()));
        if (!(envelope instanceof ClientboundCustomPayloadPacket)) {
            throw new IllegalStateException("Forge SimpleChannel did not create a clientbound custom-payload packet: "
                    + envelope.getClass().getName());
        }
        LOGGER.info("Sable Forge 1.20.1 staged production SimpleChannel and clientbound envelope verified");
    }

    private void verifyCurrentProductionPacketSet() {
        final UUID firstId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        final UUID secondId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        final Pose3d firstPose = new Pose3d();
        firstPose.position().set(1.25D, -2.5D, 3.75D);
        firstPose.rotationPoint().set(4.5D, 5.5D, 6.5D);
        final Pose3d secondPose = new Pose3d(firstPose);
        secondPose.position().add(7.0D, 8.0D, 9.0D);
        final BoundingBox3i bounds = new BoundingBox3i(-4, -3, -2, 7, 8, 9);

        verifyPacketCodecAndDiscriminator(ClientboundSableSnapshotDualPacket.CODEC,
                new ClientboundSableSnapshotDualPacket(37, List.of(
                        new ClientboundSableSnapshotDualPacket.Entry(41L, firstPose,
                                new Vector3f(1.0F, 2.0F, 3.0F), new Vector3f(-1.0F, -2.0F, -3.0F)))), 0);
        verifyPacketCodecAndDiscriminator(ClientboundSableSnapshotInfoDualPacket.CODEC,
                new ClientboundSableSnapshotInfoDualPacket(47, 53, true), 1);
        verifyPacketCodecAndDiscriminator(ClientboundStopMovingSubLevelPacket.CODEC,
                new ClientboundStopMovingSubLevelPacket(59L), 2);
        verifyPacketCodecAndDiscriminator(ClientboundChangeSubLevelNamePacket.CODEC,
                new ClientboundChangeSubLevelNamePacket(firstId, "codec validation"), 3);
        verifyPacketCodecAndDiscriminator(ClientboundStartTrackingSubLevelPacket.CODEC,
                new ClientboundStartTrackingSubLevelPacket(61L, firstId, firstPose, secondPose,
                        bounds, "tracked", 67), 4);
        verifyPacketCodecAndDiscriminator(ClientboundFinalizeSubLevelPacket.CODEC,
                new ClientboundFinalizeSubLevelPacket(71L), 5);
        verifyPacketCodecAndDiscriminator(ClientboundStopTrackingSubLevelPacket.CODEC,
                new ClientboundStopTrackingSubLevelPacket(73L), 6);
        verifyPacketCodecAndDiscriminator(ClientboundChangeBoundsSubLevelPacket.CODEC,
                new ClientboundChangeBoundsSubLevelPacket(79L, bounds), 7);
        verifyPacketCodecAndDiscriminator(ClientboundFreezePlayerPacket.CODEC,
                new ClientboundFreezePlayerPacket(firstId, new Vector3d(2.5D, 3.5D, 4.5D)), 8);

        verifyPacketCodecAndDiscriminator(ClientboundRecentlySplitSubLevelPacket.CODEC,
                new ClientboundRecentlySplitSubLevelPacket(firstId, secondId, secondPose), 12);
        verifyPacketCodecAndDiscriminator(ClientboundSableUDPActivationPacket.CODEC,
                new ClientboundSableUDPActivationPacket(firstId), 13);
        verifyPacketCodecAndDiscriminator(ClientboundEnterGizmoPacket.CODEC,
                new ClientboundEnterGizmoPacket(), 14);
        verifyPacketCodecAndDiscriminator(ServerboundPunchSubLevelPacket.CODEC,
                new ServerboundPunchSubLevelPacket(new BlockPos(-7, 81, 9),
                        new Vector3d(1.0D, 2.0D, 3.0D), new Vector3d(-4.0D, 5.0D, -6.0D)), 15);
        verifyPacketCodecAndDiscriminator(ServerboundGizmoMoveSubLevelPacket.CODEC,
                new ServerboundGizmoMoveSubLevelPacket(secondId, new Vector3d(7.5D, 8.5D, 9.5D)), 16);
        LOGGER.info("Sable non-datapack production TCP set verified "
                + "(14 codecs and exact discriminators; datapack packets 9-11 verify during common setup)");
    }

    private <P extends dev.ryanhcode.sable.network.tcp.SableTCPPacket> void verifyPacketCodecAndDiscriminator(
            final StreamCodec<? super FriendlyByteBuf, P> codec, final P packet,
            final int expectedDiscriminator) {
        final FriendlyByteBuf first = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(first, packet);
        final byte[] expectedBytes = new byte[first.readableBytes()];
        first.getBytes(first.readerIndex(), expectedBytes);
        final P decoded = codec.decode(first);
        if (first.readableBytes() != 0) {
            throw new IllegalStateException(packet.getClass().getSimpleName() + " left "
                    + first.readableBytes() + " unread codec bytes");
        }
        final FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(second, decoded);
        final byte[] decodedBytes = new byte[second.readableBytes()];
        second.getBytes(second.readerIndex(), decodedBytes);
        if (!Arrays.equals(expectedBytes, decodedBytes)) {
            throw new IllegalStateException(packet.getClass().getSimpleName()
                    + " did not preserve its exact encoded bytes across a round trip");
        }
        first.release();
        second.release();
        final int actualDiscriminator = this.packetManager.encodedDiscriminator(packet);
        if (actualDiscriminator != expectedDiscriminator) {
            throw new IllegalStateException(packet.getClass().getSimpleName() + " discriminator "
                    + actualDiscriminator + " != " + expectedDiscriminator);
        }
    }
}
