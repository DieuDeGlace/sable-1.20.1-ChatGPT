package dev.ryanhcode.sable;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SableServerConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SUB_LEVEL_SUBSTEPS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue SUB_LEVEL_STORAGE_PRUNING;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        SUB_LEVEL_SUBSTEPS_PER_TICK = builder
                .comment("How many times the physics simulation is stepped in every second. Higher values will be significantly more performance intensive, but will have higher accuracy.")
                .defineInRange("sub_level_substeps_per_tick", 2, 1, 10);

        SUB_LEVEL_STORAGE_PRUNING = builder
                .comment("Prunes and deletes empty sub-level storage files. Experimental.")
                .define("sub_level_storage_pruning", false);

        SPEC = builder.build();
    }
}
