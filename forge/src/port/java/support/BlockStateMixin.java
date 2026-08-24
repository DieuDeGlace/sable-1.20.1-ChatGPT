package dev.ryanhcode.sable.mixin.block_properties;

import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.BlockStateConditionSet;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(BlockState.class)
public class BlockStateMixin implements BlockStateExtension {
    @Unique @Nullable private Object[] sable$properties;

    @Override
    public void sable$loadProperties(StateDefinition<Block, BlockState> definition, PhysicsBlockPropertiesDefinition properties) {
        if (this.sable$properties == null) this.sable$properties = new Object[PhysicsBlockPropertyTypes.count()];
        this.sable$applyPropertySet(properties.properties());
        properties.overrides().ifPresent(overrides -> overrides.forEach((condition, values) -> {
            if (condition.matches(definition, (BlockState) (Object) this)) this.sable$applyPropertySet(values);
        }));
    }

    @Unique
    private void sable$applyPropertySet(Map<ResourceLocation, Object> properties) {
        properties.forEach((id, value) -> this.sable$properties[PhysicsBlockPropertyTypes.getPropertyType(id).id()] = value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T sable$getProperty(PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<T> type) {
        return this.sable$properties == null || this.sable$properties[type.id()] == null
                ? type.defaultValue() : (T) this.sable$properties[type.id()];
    }
}
