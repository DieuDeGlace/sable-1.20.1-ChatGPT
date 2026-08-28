package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SafeBlockEntityRenderer.class)
public class SafeBlockEntityRendererMixin {
    @ModifyVariable(method = "shouldCullItem", at = @At("HEAD"), remap = false, argsOnly = true)
    public Vec3 sable$projectItemPos(final Vec3 itemPos) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(itemPos);
        if (subLevel == null) {
            return itemPos;
        }

        // This method runs during rendering. Use the interpolated render pose directly;
        // the generic projection helper defaults to the logical physics pose outside a
        // raycast pose scope and can cull transported belt items at the wrong location.
        return subLevel.renderPose().transformPosition(itemPos);
    }
}
