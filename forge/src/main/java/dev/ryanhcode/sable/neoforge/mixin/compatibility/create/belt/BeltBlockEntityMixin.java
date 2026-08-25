package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.belt;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(BeltBlockEntity.class)
public abstract class BeltBlockEntityMixin extends KineticBlockEntity {

    @Unique private static final Logger sable$BELT_LOGGER = LogUtils.getLogger();
    @Unique private static final AtomicInteger sable$beltNbtLogCount = new AtomicInteger();

    public BeltBlockEntityMixin(final BlockEntityType<?> typeIn, final BlockPos pos, final BlockState state) {
        super(typeIn, pos, state);
    }

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void sable$traceBeltWrite(final CompoundTag compound,
                                      final boolean clientPacket,
                                      final CallbackInfo ci) {
        sable$traceBeltNbt("WRITE", compound, clientPacket);
    }

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private void sable$traceBeltRead(final CompoundTag compound,
                                     final boolean clientPacket,
                                     final CallbackInfo ci) {
        sable$traceBeltNbt("READ", compound, clientPacket);
    }

    @Unique
    private void sable$traceBeltNbt(final String operation,
                                    final CompoundTag compound,
                                    final boolean clientPacket) {
        if (!compound.contains("Inventory", Tag.TAG_COMPOUND)) {
            return;
        }
        final CompoundTag inventory = compound.getCompound("Inventory");
        final int itemCount = inventory.getList("Items", Tag.TAG_COMPOUND).size();
        final boolean hasLazyItem = inventory.contains("LazyItem", Tag.TAG_COMPOUND);
        if (itemCount == 0 && !hasLazyItem) {
            return;
        }
        final int logIndex = sable$beltNbtLogCount.getAndIncrement();
        if (logIndex >= 128) {
            return;
        }
        final String side = this.level == null ? "no-level" : this.level.isClientSide ? "client" : "server";
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(sable$BELT_LOGGER, "SABLE-BELT-NBT-{}: side={}, clientPacket={}, pos={}, items={}, lazy={}, "
                        + "controller={}, length={}, logIndex={}",
                operation, side, clientPacket, this.worldPosition, itemCount, hasLazyItem,
                compound.getBoolean("IsController"), compound.getInt("Length"), logIndex);
    }

    @Override
    public void onSpeedChanged(final float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container instanceof final ServerSubLevelContainer serverSubLevelContainer) {
            final SubLevelPhysicsSystem physicsSystem = serverSubLevelContainer.physicsSystem();

            final BlockPos blockPos = this.getBlockPos();
            physicsSystem.wakeUpObjectsAt(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            final SubLevel subLevel = Sable.HELPER.getContaining(this);
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                physicsSystem.getPipeline().wakeUp(serverSubLevel);
            }
        }
    }
}
