package dev.ryanhcode.sable.neoforge.mixin.compatibility.mts;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.compatibility.mts.MtsVehiclePlacementCompatibility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Projects only MTS vehicle-item placement into visible-world coordinates.
 * Other MTS items and ordinary-world placement retain the original call and
 * arguments exactly.
 */
@Pseudo
@Mixin(targets = "mcinterface1201.BuilderItem", remap = false)
public abstract class BuilderItemMixin {

    @WrapOperation(
            method = {"useOn", "m_6225_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lminecrafttransportsimulator/items/components/AItemBase;onBlockClicked(" +
                            "Lminecrafttransportsimulator/mcinterface/AWrapperWorld;" +
                            "Lminecrafttransportsimulator/mcinterface/IWrapperPlayer;" +
                            "Lminecrafttransportsimulator/baseclasses/Point3D;" +
                            "Lminecrafttransportsimulator/blocks/components/ABlockBase$Axis;)Z",
                    remap = false
            ),
            remap = false,
            require = 0,
            expect = 1
    )
    private boolean sable$projectVehiclePlacement(@Coerce final Object wrappedItem,
                                                   @Coerce final Object world,
                                                   @Coerce final Object player,
                                                   @Coerce final Object point,
                                                   @Coerce final Object axis,
                                                   final Operation<Boolean> original,
                                                   @Local(argsOnly = true) final UseOnContext context) {
        if (!MtsVehiclePlacementCompatibility.isVehicleItem(wrappedItem)) {
            return original.call(wrappedItem, world, player, point, axis);
        }

        final Level level = context.getLevel();
        if (level.isClientSide) {
            return original.call(wrappedItem, world, player, point, axis);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, context.getClickedPos());
        if (subLevel == null) {
            return original.call(wrappedItem, world, player, point, axis);
        }

        final Vec3 projectedOrigin = MtsVehiclePlacementCompatibility.projectClickedBlockOrigin(
                subLevel.logicalPose(), context.getClickedPos());
        MtsVehiclePlacementCompatibility.trySetPointCoordinates(point, projectedOrigin);
        return original.call(wrappedItem, world, player, point, axis);
    }
}
