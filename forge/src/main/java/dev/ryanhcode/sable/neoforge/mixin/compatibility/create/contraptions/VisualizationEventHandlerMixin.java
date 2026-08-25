package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.visualization.VisualizationEventHandler;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisualizationEventHandler.class)
public class VisualizationEventHandlerMixin {

    @Inject(method = "onEntityJoinLevel", at = @At("TAIL"))
    private static void sable$onEntityJoinLevel(final Level level, final Entity entity, final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(entity);

        if (entity instanceof final AbstractContraptionEntity contraptionEntity) {
            final SubLevelContainer container = SubLevelContainer.getContainer(level);
            final boolean inPlotBounds = container != null && container.inBounds(entity.chunkPosition());
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-CREATE-CONTRAPTION-JOIN: uuid={}, entityId={}, type={}, client={}, "
                            + "position={}, chunk={}, alive={}, managerPresent={}, canVisualize={}, "
                            + "containerPresent={}, inPlotBounds={}, subLevel={}, blocks={}",
                    entity.getUUID(), entity.getId(), entity.getType(), level.isClientSide,
                    entity.position(), entity.chunkPosition(), entity.isAlive(),
                    VisualizationManager.get(level) != null, VisualizationHelper.canVisualize(entity),
                    container != null, inPlotBounds,
                    subLevel == null ? "none" : subLevel.getUniqueId(),
                    contraptionEntity.getContraption() == null
                            ? -1 : contraptionEntity.getContraption().getBlocks().size());
        }

        if (subLevel != null) {
            FlywheelCompatNeoForge.createRenderInfo(level, subLevel);
        }
    }
}
