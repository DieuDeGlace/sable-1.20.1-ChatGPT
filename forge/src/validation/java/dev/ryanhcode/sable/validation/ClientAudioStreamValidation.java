package dev.ryanhcode.sable.validation;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/** Runtime proof that Forge's custom SoundInstance stream hook survives delegation. */
final class ClientAudioStreamValidation {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientAudioStreamValidation() {
    }

    static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            final CompletableFuture<AudioStream> marker = new CompletableFuture<>();
            final AbstractSoundInstance source = new AbstractSoundInstance(
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, RandomSource.create(0L)) {
                @Override
                public CompletableFuture<AudioStream> getStream(final SoundBufferLibrary soundBuffers,
                                                                  final Sound sound,
                                                                  final boolean looping) {
                    return marker;
                }
            };
            final MovingSoundInstanceDelegate delegate = new MovingSoundInstanceDelegate(source, null);
            final CompletableFuture<AudioStream> actual = delegate.getStream(null, null, false);
            if (actual != marker) {
                throw new IllegalStateException("MovingSoundInstanceDelegate did not forward Forge getStream");
            }
            LOGGER.info("Sable CUSTOM SOUND STREAM DELEGATION VERIFIED");
        });
    }
}
