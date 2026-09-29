package net.unfamily.iskalib;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class IskaLibConfig {
    private IskaLibConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue FTB_TEAMS_SYNC_ENABLED = BUILDER
            .comment("When true and FTB Teams is loaded, shop teams can sync with FTB team membership.")
            .define("ftbTeamsSyncEnabled", true);

    static {
        BUILDER.comment("Stage-related gates used by Library integrations").push("stages");
        BUILDER.comment("Ultimine stage gate (consumed by Ultimine integration)").push("ultimine");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_ULTIMINE_STAGE_GATE = BUILDER
            .comment("When true, Ultimine requires the configured stage before use")
            .define("enable_ultimine_stage_gate", false);

    public static final ModConfigSpec.ConfigValue<String> ULTIMINE_REQUIRED_STAGE = BUILDER
            .comment("Stage id required for Ultimine when the gate is enabled (empty = none)")
            .define("ultimine_required_stage", "");

    public static final ModConfigSpec.ConfigValue<String> ULTIMINE_STAGE_SCOPE = BUILDER
            .comment("Scope for the Ultimine stage check: player, world, or team")
            .define(
                    "ultimine_stage_scope",
                    "player",
                    value -> value instanceof String scope
                            && (scope.equalsIgnoreCase("player")
                            || scope.equalsIgnoreCase("world")
                            || scope.equalsIgnoreCase("team")));

    static {
        BUILDER.pop(2);
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
