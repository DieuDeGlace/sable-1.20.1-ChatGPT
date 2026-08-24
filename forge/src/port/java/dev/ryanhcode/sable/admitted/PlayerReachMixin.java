package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Forge 1.20.1 equivalent of upstream PlayerMixin's interaction-distance hooks. */
@Mixin(Player.class)
public abstract class PlayerReachMixin extends LivingEntity {
    protected PlayerReachMixin(final EntityType<? extends LivingEntity> type, final Level level) {
        super(type, level);
    }

    public boolean canReach(final BlockPos pos, final double slop) {
        final IForgePlayer forgePlayer = (IForgePlayer) this;
        final double range = forgePlayer.getBlockReach() + slop;
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), pos);
        if (subLevel != null) {
            final Vec3 eye = subLevel.logicalPose().transformPositionInverse(this.getEyePosition());
            if (new AABB(pos).distanceToSqr(eye) < range * range) {
                return true;
            }
        }
        return this.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < range * range;
    }

    public boolean canReach(final Entity entity, final double slop) {
        final IForgePlayer forgePlayer = (IForgePlayer) this;
        final double range = forgePlayer.getEntityReach() + slop;
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), entity.position());
        if (subLevel != null) {
            final Vec3 eye = subLevel.logicalPose().transformPositionInverse(this.getEyePosition());
            if (entity.getBoundingBox().distanceToSqr(eye) < range * range) {
                return true;
            }
        }
        return forgePlayer.isCloseEnough(entity, range);
    }
}
