package net.unfamily.iskalib.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.unfamily.iskalib.IskaLibConfig;
import org.slf4j.Logger;

/**
 * Scans mod jars + configured datapack roots for recipe JSON, runs
 * {@link RecipeBundleSplitter}, and builds overlay bytes for the virtual pack.
 */
public final class RecipeBundleBridgeBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final String DISABLED_PARENT_TEMPLATE =
            "{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}],\"type\":\"%s\"}";

    private RecipeBundleBridgeBuilder() {}

    public static Map<ResourceLocation, byte[]> buildOverlayBytes() {
        Map<ResourceLocation, JsonElement> discovered = new LinkedHashMap<>();
        scanModJars(discovered);
        scanBootstrapRoots(discovered);

        if (discovered.isEmpty()) {
            return Map.of();
        }

        Map<ResourceLocation, JsonElement> working = new LinkedHashMap<>(discovered);
        RecipeBundleSplitter.splitInPlace(working, ResourceLocation::parse, ResourceLocation::toString);

        Map<ResourceLocation, byte[]> overlay = new LinkedHashMap<>();
        for (ResourceLocation parentId : discovered.keySet()) {
            if (!working.containsKey(parentId)) {
                JsonElement original = discovered.get(parentId);
                String type = "minecraft:crafting_shapeless";
                if (original != null && original.isJsonObject() && original.getAsJsonObject().has("type")) {
                    type = original.getAsJsonObject().get("type").getAsString();
                }
                String json = DISABLED_PARENT_TEMPLATE.formatted(type);
                overlay.put(recipeIdToResource(parentId), json.getBytes(StandardCharsets.UTF_8));
            }
        }
        for (Map.Entry<ResourceLocation, JsonElement> entry : working.entrySet()) {
            if (!discovered.containsKey(entry.getKey())) {
                overlay.put(recipeIdToResource(entry.getKey()), GSON.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            }
        }

        if (!overlay.isEmpty()) {
            LOGGER.info(
                    "Recipe bundle bridge: {} overlay resource(s) ({} source recipe file(s))",
                    overlay.size(),
                    discovered.size());
        }
        return overlay;
    }

    private static ResourceLocation recipeIdToResource(ResourceLocation recipeId) {
        return ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(), "recipe/" + recipeId.getPath() + ".json");
    }

    private static void scanModJars(Map<ResourceLocation, JsonElement> out) {
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile file = info.getFile();
            // Prefer SecureJar / findResource("data"): getFilePath() in Gradle runs is often
            // classes/java/main without resources, so Colossal bundles were never split.
            if (scanModFileData(file, out)) {
                continue;
            }
            Path path = file.getFilePath();
            if (path == null) {
                continue;
            }
            if (Files.isRegularFile(path) && isArchive(path)) {
                scanZipDataRecipes(path, out);
            } else if (Files.isDirectory(path)) {
                Path data = path.resolve("data");
                if (Files.isDirectory(data)) {
                    scanDataRootRecipes(data, out);
                }
            }
        }
    }

    /** @return true if a data root was found and scanned (even if empty of recipes) */
    private static boolean scanModFileData(IModFile file, Map<ResourceLocation, JsonElement> out) {
        try {
            Path data = file.findResource("data");
            if (data != null && Files.isDirectory(data)) {
                scanDataRootRecipes(data, out);
                return true;
            }
        } catch (Exception ex) {
            LOGGER.debug("Recipe bridge: findResource(data) failed for {}: {}", file.getFileName(), ex.toString());
        }
        try {
            Path root = file.getSecureJar().getRootPath();
            Path data = root.resolve("data");
            if (Files.isDirectory(data)) {
                scanDataRootRecipes(data, out);
                return true;
            }
        } catch (Exception ex) {
            LOGGER.debug("Recipe bridge: SecureJar data scan failed for {}: {}", file.getFileName(), ex.toString());
        }
        return false;
    }

    private static void scanBootstrapRoots(Map<ResourceLocation, JsonElement> out) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        for (String raw : IskaLibConfig.resolveBootstrapDatapackPaths()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path root = gameDir.resolve(raw.trim()).normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root) && isArchive(root)) {
                scanZipDataRecipes(root, out);
                continue;
            }
            if (!Files.isDirectory(root)) {
                continue;
            }
            // Direct data root (e.g. kubejs/data)
            if (Files.isDirectory(root.resolve("data")) || looksLikeDataRoot(root)) {
                if (Files.isDirectory(root.resolve("data"))) {
                    scanDataRootRecipes(root.resolve("data"), out);
                } else {
                    scanDataRootRecipes(root, out);
                }
            }
            // Pack repository folder
            try (Stream<Path> children = Files.list(root)) {
                for (Path child : children.toList()) {
                    if (Files.isRegularFile(child) && isArchive(child)) {
                        scanZipDataRecipes(child, out);
                    } else if (Files.isDirectory(child)) {
                        Path data = child.resolve("data");
                        if (Files.isDirectory(data)) {
                            scanDataRootRecipes(data, out);
                        }
                    }
                }
            } catch (IOException ex) {
                LOGGER.debug("Recipe bridge: could not list {}: {}", root, ex.toString());
            }
        }
    }

    private static boolean looksLikeDataRoot(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            return children.anyMatch(p -> Files.isDirectory(p) && Files.isDirectory(p.resolve("recipe")));
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static void scanZipDataRecipes(Path zip, Map<ResourceLocation, JsonElement> out) {
        try (FileSystem fs = FileSystems.newFileSystem(zip)) {
            Path data = fs.getPath("data");
            if (Files.isDirectory(data)) {
                scanDataRootRecipes(data, out);
            }
        } catch (IOException ex) {
            LOGGER.debug("Recipe bridge: skip archive {}: {}", zip, ex.toString());
        }
    }

    private static void scanDataRootRecipes(Path dataRoot, Map<ResourceLocation, JsonElement> out) {
        if (!Files.isDirectory(dataRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dataRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                Path rel = dataRoot.relativize(file);
                if (rel.getNameCount() < 3) {
                    continue;
                }
                // <ns>/recipe/...
                if (!"recipe".equals(rel.getName(1).toString())) {
                    continue;
                }
                String ns = rel.getName(0).toString();
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 2; i < rel.getNameCount(); i++) {
                    if (i > 2) {
                        pathBuilder.append('/');
                    }
                    pathBuilder.append(rel.getName(i).toString());
                }
                String fileName = pathBuilder.toString();
                if (!fileName.endsWith(".json")) {
                    continue;
                }
                String recipePath = fileName.substring(0, fileName.length() - 5);
                ResourceLocation id = ResourceLocation.tryParse(ns + ":" + recipePath);
                if (id == null) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement el = GSON.fromJson(reader, JsonElement.class);
                    if (el != null && el.isJsonObject()) {
                        out.put(id, el);
                    }
                } catch (IOException | JsonParseException | IllegalArgumentException ex) {
                    LOGGER.debug("Recipe bridge: failed reading {}: {}", file, ex.toString());
                }
            }
        } catch (IOException ex) {
            LOGGER.debug("Recipe bridge: walk failed for {}: {}", dataRoot, ex.toString());
        }
    }
}
