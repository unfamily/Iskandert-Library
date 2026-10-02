package net.unfamily.iskalib;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IskaLibConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(IskaLibConfig.class);

    private IskaLibConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue FTB_TEAMS_SYNC_ENABLED = BUILDER
            .comment("When true and FTB Teams is loaded, shop teams can sync with FTB team membership.")
            .define("ftbTeamsSyncEnabled", true);

    public static final ModConfigSpec.BooleanValue EXPLOSION_GRIEF_FTB_CLAIMS = BUILDER
            .comment(
                    "When false (default), Library progressive explosions do not break blocks in FTB Chunks claimed chunks.",
                    "Entity damage still applies. When true, claimed chunks can be griefed by those explosions.",
                    "No effect if FTB Chunks is not loaded.")
            .define("explosionGriefFtbClaims", false);

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
        BUILDER.comment(
                        "Filesystem bootstrap for JSON fluids/tools/gases before a ResourceManager exists.",
                        "Paths are relative to the game directory. Folders and .zip/.jar packs are read in-place (not extracted).",
                        "Order = merge priority (later entries override earlier).")
                .push("bootstrap");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOOTSTRAP_DATAPACK_PATHS = BUILDER
            .comment(
                    "Data/pack roots to scan at mod init (KubeJS data root, Open Loader, Global Packs, instance datapacks).",
                    "A path is a pack root if it contains .zip/.jar children or folders with pack.mcmeta; otherwise a data root.")
            .defineList(
                    "datapack_paths",
                    List.of(
                            "kubejs/data",
                            "datapacks",
                            "config/openloader/data",
                            "global_packs/required_data",
                            "global_packs/optional_data"),
                    obj -> obj instanceof String s && !s.isBlank());

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Safe for mod-construction bootstrap: NeoForge has not loaded COMMON config yet, so {@link
     * ModConfigSpec.ConfigValue#get()} would throw. Prefer loaded values, else peek on-disk toml, else defaults.
     */
    public static List<? extends String> resolveBootstrapDatapackPaths() {
        if (SPEC.isLoaded()) {
            return BOOTSTRAP_DATAPACK_PATHS.get();
        }
        List<? extends String> peeked = peekBootstrapDatapackPathsFromDisk();
        if (peeked != null && !peeked.isEmpty()) {
            return peeked;
        }
        return BOOTSTRAP_DATAPACK_PATHS.getDefault();
    }

    private static List<? extends String> peekBootstrapDatapackPathsFromDisk() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("iska_lib-common.toml");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build()) {
            file.load();
            Object raw = file.get("bootstrap.datapack_paths");
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof String s && !s.isBlank()) {
                    out.add(s);
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception ex) {
            LOGGER.debug("Could not peek bootstrap.datapack_paths from {}: {}", path, ex.getMessage());
            return null;
        }
    }
}
