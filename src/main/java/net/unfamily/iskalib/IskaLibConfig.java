package net.unfamily.iskalib;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class IskaLibConfig {
    private IskaLibConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue FTB_TEAMS_SYNC_ENABLED = BUILDER
            .comment("When true and FTB Teams is loaded, shop teams can sync with FTB team membership.")
            .define("ftbTeamsSyncEnabled", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

