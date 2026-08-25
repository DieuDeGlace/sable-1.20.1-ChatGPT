package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltRenderer;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Make upright items face the the camera properly on belts
 */
@Mixin(BeltRenderer.class)
public class BeltRendererMixin {

    @Unique private static final Logger sable$BELT_RENDER_LOGGER = LogUtils.getLogger();
    @Unique private static final Set<String> sable$beltInventoryStates = ConcurrentHashMap.newKeySet();
    @Unique private static final Set<String> sable$beltRenderItems = ConcurrentHashMap.newKeySet();
    @Unique private static final AtomicInteger sable$beltRenderLogCount = new AtomicInteger();

    @Inject(method = "renderItems", at = @At("HEAD"), remap = false)
    private void sable$traceRenderItems(final BeltBlockEntity be,
                                        final float partialTicks,
                                        final PoseStack poseStack,
                                        final MultiBufferSource buffer,
                                        final int light,
                                        final int overlay,
                                        final CallbackInfo ci) {
        final BeltInventory inventory = be.getInventory();
        final int itemCount = inventory == null ? -1 : inventory.getTransportedItems().size();
        final boolean hasLazyItem = inventory != null && inventory.getLazyClientItem() != null;
        final String state = be.getBlockPos().asLong() + ":" + be.isController() + ":" + be.beltLength
                + ":" + itemCount + ":" + hasLazyItem;
        if (sable$beltInventoryStates.size() >= 128 || !sable$beltInventoryStates.add(state)) {
            return;
        }
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(sable$BELT_RENDER_LOGGER, "SABLE-BELT-RENDER-INVENTORY: pos={}, controller={}, length={}, "
                        + "items={}, lazy={}, partialTick={}, matrixTranslation=({}, {}, {})",
                be.getBlockPos(), be.isController(), be.beltLength, itemCount, hasLazyItem, partialTicks,
                poseStack.last().pose().m30(), poseStack.last().pose().m31(), poseStack.last().pose().m32());
    }

    @Inject(method = "renderItem", at = @At("HEAD"), remap = false)
    private void sable$traceRenderItem(final BeltBlockEntity be,
                                       final float partialTicks,
                                       final PoseStack poseStack,
                                       final MultiBufferSource buffer,
                                       final int light,
                                       final int overlay,
                                       final Direction beltFacing,
                                       final Vec3i directionVec,
                                       final BeltSlope slope,
                                       final int verticality,
                                       final boolean slopeAlongX,
                                       final boolean onContraption,
                                       final TransportedItemStack transported,
                                       final Vec3 beltStartOffset,
                                       final CallbackInfo ci) {
        final String key = be.getBlockPos().asLong() + ":" + transported.stack.getItem()
                + ":" + transported.insertedAt;
        if (sable$beltRenderItems.size() >= 128 || !sable$beltRenderItems.add(key)) {
            return;
        }
        final int logIndex = sable$beltRenderLogCount.getAndIncrement();
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(sable$BELT_RENDER_LOGGER, "SABLE-BELT-RENDER-ITEM: pos={}, stack={}, count={}, beltPosition={}, "
                        + "prevPosition={}, sideOffset={}, facing={}, slope={}, localMatrix=({}, {}, {}), logIndex={}",
                be.getBlockPos(), transported.stack.getItem(), transported.stack.getCount(),
                transported.beltPosition, transported.prevBeltPosition, transported.sideOffset,
                beltFacing, slope, poseStack.last().pose().m30(), poseStack.last().pose().m31(),
                poseStack.last().pose().m32(), logIndex);
    }

    @ModifyExpressionValue(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$renderViewEntityPosition(final Vec3 original, @Local(argsOnly = true) final BeltBlockEntity be) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(be);
        if (subLevel != null) {
            return subLevel.renderPose().transformPositionInverse(original);
        } else {
            return original;
        }
    }
}
