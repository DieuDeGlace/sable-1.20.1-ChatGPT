package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(SafeBlockEntityRenderer.class)
public class SafeBlockEntityRendererMixin {
    @Unique private static final Logger sable$LOGGER = LogUtils.getLogger();
    @Unique private static final Set<java.util.UUID> sable$loggedItemCullPaths = ConcurrentHashMap.newKeySet();
    @Unique private static final AtomicInteger sable$cullResultLogCount = new AtomicInteger();
    @Unique private static final ThreadLocal<String> sable$cullContext = new ThreadLocal<>();

    @ModifyVariable(method = "shouldCullItem", at = @At("HEAD"), remap = false, argsOnly = true)
    public Vec3 sable$projectItemPos(final Vec3 itemPos) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(itemPos);
        if (subLevel == null) {
            sable$cullContext.set("sub-level=none, input=" + itemPos);
            return itemPos;
        }

        // This method runs during rendering. Use the interpolated render pose directly;
        // the generic projection helper defaults to the logical physics pose outside a
        // raycast pose scope and can cull transported belt items at the wrong location.
        final Vec3 physical = subLevel.renderPose().transformPosition(itemPos);
        sable$cullContext.set("sub-level=" + subLevel.getUniqueId() + ", local=" + itemPos
                + ", physical=" + physical);
        if (sable$loggedItemCullPaths.size() < 64 && sable$loggedItemCullPaths.add(subLevel.getUniqueId())) {
            sable$LOGGER.info("SABLE-CREATE-ITEM-CULL: sub-level={}, local={}, physical={}, render-pose=true",
                    subLevel.getUniqueId(), itemPos, physical);
        }
        return physical;
    }

    @Inject(method = "shouldCullItem", at = @At("RETURN"), remap = false)
    private void sable$traceCullResult(final Vec3 itemPos,
                                       final Level level,
                                       final CallbackInfoReturnable<Boolean> cir) {
        final String context = sable$cullContext.get();
        sable$cullContext.remove();
        final int logIndex = sable$cullResultLogCount.getAndIncrement();
        if (logIndex >= 128) {
            return;
        }
        sable$LOGGER.info("SABLE-CREATE-ITEM-CULL-RESULT: {}, effectiveArg={}, culled={}, level={}, logIndex={}",
                context, itemPos, cir.getReturnValue(), level.getClass().getName(), logIndex);
    }
}
