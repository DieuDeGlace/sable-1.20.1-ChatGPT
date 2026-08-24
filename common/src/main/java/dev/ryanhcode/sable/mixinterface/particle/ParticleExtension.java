package dev.ryanhcode.sable.mixinterface.particle;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;

public interface ParticleExtension {

    Vec3 sable$getPosition();

    Vec3 sable$getVelocity();

    void sable$setVelocity(Vec3 velocity);

    void sable$initialKickOut();

    void sable$moveWithInheritedVelocity();

    void sable$setTrackingSubLevel(ClientSubLevel subLevel, Vec3 particlePosition);

    SubLevel sable$getTrackingSubLevel();
}
