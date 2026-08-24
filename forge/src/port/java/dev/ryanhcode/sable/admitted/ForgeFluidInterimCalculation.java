package dev.ryanhcode.sable.admitted;

import net.minecraft.world.phys.Vec3;

/** Mutable per-fluid accumulator used by the Forge 1.20.1 entity-fluid adapter. */
public final class ForgeFluidInterimCalculation {
    public double fluidHeight;
    public Vec3 flowVector = Vec3.ZERO;
    public int blockCount;
}
