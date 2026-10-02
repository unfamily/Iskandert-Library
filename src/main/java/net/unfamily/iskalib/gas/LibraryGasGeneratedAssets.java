package net.unfamily.iskalib.gas;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes per-gas blockstates that point at the shared {@code iska_lib:block/gas} model.
 * Without these, world gas is invisible (fluid mesh is suppressed in favor of the block model).
 */
public final class LibraryGasGeneratedAssets {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryGasGeneratedAssets.class);

    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 34,
                "description": "IskaLib generated gas blockstates"
              }
            }
            """;

    private static final String BLOCKSTATE_JSON = """
            {
              "variants": {
                "collectable=false,level=0": { "model": "iska_lib:block/gas" },
                "collectable=true,level=0": { "model": "iska_lib:block/gas" },
                "collectable=false,level=1": { "model": "iska_lib:block/gas" },
                "collectable=true,level=1": { "model": "iska_lib:block/gas" },
                "collectable=false,level=2": { "model": "iska_lib:block/gas" },
                "collectable=true,level=2": { "model": "iska_lib:block/gas" },
                "collectable=false,level=3": { "model": "iska_lib:block/gas" },
                "collectable=true,level=3": { "model": "iska_lib:block/gas" },
                "collectable=false,level=4": { "model": "iska_lib:block/gas" },
                "collectable=true,level=4": { "model": "iska_lib:block/gas" },
                "collectable=false,level=5": { "model": "iska_lib:block/gas" },
                "collectable=true,level=5": { "model": "iska_lib:block/gas" },
                "collectable=false,level=6": { "model": "iska_lib:block/gas" },
                "collectable=true,level=6": { "model": "iska_lib:block/gas" },
                "collectable=false,level=7": { "model": "iska_lib:block/gas" },
                "collectable=true,level=7": { "model": "iska_lib:block/gas" },
                "collectable=false,level=8": { "model": "iska_lib:block/gas" },
                "collectable=true,level=8": { "model": "iska_lib:block/gas" },
                "collectable=false,level=9": { "model": "iska_lib:block/gas" },
                "collectable=true,level=9": { "model": "iska_lib:block/gas" },
                "collectable=false,level=10": { "model": "iska_lib:block/gas" },
                "collectable=true,level=10": { "model": "iska_lib:block/gas" },
                "collectable=false,level=11": { "model": "iska_lib:block/gas" },
                "collectable=true,level=11": { "model": "iska_lib:block/gas" },
                "collectable=false,level=12": { "model": "iska_lib:block/gas" },
                "collectable=true,level=12": { "model": "iska_lib:block/gas" },
                "collectable=false,level=13": { "model": "iska_lib:block/gas" },
                "collectable=true,level=13": { "model": "iska_lib:block/gas" },
                "collectable=false,level=14": { "model": "iska_lib:block/gas" },
                "collectable=true,level=14": { "model": "iska_lib:block/gas" },
                "collectable=false,level=15": { "model": "iska_lib:block/gas" },
                "collectable=true,level=15": { "model": "iska_lib:block/gas" }
              }
            }
            """;

    private LibraryGasGeneratedAssets() {}

    public static Path assetsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("iska_lib/generated_gas_assets");
    }

    public static void writeBlockstate(String modId, String blockPath) {
        if (modId == null || modId.isBlank() || blockPath == null || blockPath.isBlank()) {
            return;
        }
        Path root = assetsRoot();
        try {
            Files.createDirectories(root);
            Path meta = root.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, PACK_META, StandardCharsets.UTF_8);
            }
            Path dir = root.resolve("assets").resolve(modId).resolve("blockstates");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(blockPath + ".json"), BLOCKSTATE_JSON, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.warn("Failed to write gas blockstate for {}:{}: {}", modId, blockPath, ex.toString());
        }
    }
}
