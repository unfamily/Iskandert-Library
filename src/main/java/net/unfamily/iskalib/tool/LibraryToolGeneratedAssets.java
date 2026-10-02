package net.unfamily.iskalib.tool;

import net.neoforged.fml.loading.FMLPaths;
import net.unfamily.iskalib.IskaLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes placeholder client assets for Library tools created at startup (common-side filesystem only).
 */
public final class LibraryToolGeneratedAssets {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryToolGeneratedAssets.class);
    private static final String ITEM_JSON = """
            {
              "model": {
                "type": "minecraft:model",
                "model": "minecraft:item/iron_pickaxe"
              }
            }
            """;
    private static final String MODEL_JSON = """
            {
              "parent": "minecraft:item/iron_pickaxe"
            }
            """;
    private static final String PACK_META = """
            {
              "pack": {
                "description": "IskaLib generated tool item models",
                "min_format": [84, 0],
                "max_format": [84, 0]
              }
            }
            """;

    private LibraryToolGeneratedAssets() {}

    public static Path assetsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("iska_lib/generated_tool_assets");
    }

    public static void writePlaceholderAssets(String itemPath) {
        if (itemPath == null || itemPath.isBlank()) {
            return;
        }
        Path root = assetsRoot();
        try {
            Files.createDirectories(root);
            Path meta = root.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, PACK_META, StandardCharsets.UTF_8);
            }
            Path itemsDir = root.resolve("assets").resolve(IskaLib.MOD_ID).resolve("items");
            Files.createDirectories(itemsDir);
            Files.writeString(itemsDir.resolve(itemPath + ".json"), ITEM_JSON, StandardCharsets.UTF_8);

            Path modelsDir = root.resolve("assets").resolve(IskaLib.MOD_ID).resolve("models").resolve("item");
            Files.createDirectories(modelsDir);
            Path modelJson = modelsDir.resolve(itemPath + ".json");
            if (!Files.exists(modelJson)) {
                Files.writeString(modelJson, MODEL_JSON, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to write generated tool assets for {}: {}", itemPath, ex.toString());
        }
    }
}
