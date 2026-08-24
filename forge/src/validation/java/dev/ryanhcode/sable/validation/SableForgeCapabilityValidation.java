package dev.ryanhcode.sable.validation;

import dev.ryanhcode.sable.forge.SableForgeBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/**
 * A non-empty third-party-style Forge chunk capability used only by the
 * explicit M4 assembly validation phases. It proves that Sable's production
 * plot serializer preserves arbitrary provider NBT rather than only Sable data.
 */
final class SableForgeCapabilityValidation {
    static final int SENTINEL = 0x5A8E2020;
    private static final String VALUE_KEY = "value";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SableForgeBootstrap.MOD_ID, "m4_chunk_capability_probe");
    private static final Capability<ProbeValue> CAPABILITY = CapabilityManager.get(
            new CapabilityToken<>() {
            });

    private SableForgeCapabilityValidation() {
    }

    static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.register(ProbeValue.class);
    }

    static void attachChunkCapability(final AttachCapabilitiesEvent<LevelChunk> event) {
        if (!isValidationPhase()) {
            return;
        }
        final Provider provider = new Provider();
        event.addCapability(ID, provider);
        event.addListener(provider::invalidate);
    }

    static void set(final LevelChunk chunk, final int value) {
        final ProbeValue probe = chunk.getCapability(CAPABILITY).orElseThrow(
                () -> new IllegalStateException("M4 plot chunk capability probe was not attached"));
        probe.value = value;
    }

    static int get(final LevelChunk chunk) {
        return chunk.getCapability(CAPABILITY).map(probe -> probe.value).orElseThrow(
                () -> new IllegalStateException("M4 plot chunk capability probe was not attached"));
    }

    private static boolean isValidationPhase() {
        final String phase = System.getProperty("sable.m4ValidationPhase", "");
        return "assembly-create".equals(phase) || "assembly-reload".equals(phase);
    }

    static final class ProbeValue {
        private int value;
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ProbeValue value = new ProbeValue();
        private final LazyOptional<ProbeValue> optional = LazyOptional.of(() -> this.value);

        @Override
        public <T> LazyOptional<T> getCapability(final Capability<T> capability, final Direction side) {
            return capability == CAPABILITY ? this.optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt(VALUE_KEY, this.value.value);
            return tag;
        }

        @Override
        public void deserializeNBT(final CompoundTag tag) {
            this.value.value = tag.getInt(VALUE_KEY);
        }

        private void invalidate() {
            this.optional.invalidate();
        }
    }
}
