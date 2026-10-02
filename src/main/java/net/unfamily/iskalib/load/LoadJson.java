package net.unfamily.iskalib.load;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Collects JSON under {@code data/<namespace>/load/…} for Library-owned catalogs.
 * <p>
 * Namespace is always dynamic (any datapack/mod namespace). Matching is primarily by JSON
 * {@code type}; recommended folder names under {@code load/} are conventions only.
 */
public final class LoadJson {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadJson.class);
    private static final Gson GSON = new Gson();
    public static final String LOAD_FOLDER = "load";

    private LoadJson() {}

    /**
     * Collects every JSON under {@code load/} (any subfolder) whose root {@code type} is in
     * {@code acceptedTypes}. Works for any datapack namespace.
     */
    public static Map<Identifier, JsonElement> collectMergedJsonForTypes(
            ResourceManager resourceManager,
            Set<String> acceptedTypes) {
        if (acceptedTypes == null || acceptedTypes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return collectFromStacks(
                resourceManager,
                LOAD_FOLDER,
                id -> id.getPath().endsWith(".json") && isUnderLoadTree(id),
                parsed -> typeMatches(parsed, acceptedTypes));
    }

    /**
     * Collects every JSON under {@code data/<any>/load/<subdir>/…} (any namespace), no type filter.
     */
    public static Map<Identifier, JsonElement> collectMergedJsonUnderLoadSubdir(
            ResourceManager resourceManager,
            String subdirUnderLoad) {
        String prefix = LOAD_FOLDER + "/" + subdirUnderLoad + "/";
        return collectFromStacks(
                resourceManager,
                LOAD_FOLDER,
                id -> id.getPath().endsWith(".json") && id.getPath().startsWith(prefix),
                parsed -> true);
    }

    /**
     * Collects every JSON under {@code data/<any>/<directoryUnderDataNamespace>/…}.
     */
    public static Map<Identifier, JsonElement> collectMergedJsonUnderDataDir(
            ResourceManager resourceManager,
            String directoryUnderDataNamespace) {
        return collectFromStacks(
                resourceManager,
                directoryUnderDataNamespace,
                id -> id.getPath().endsWith(".json"),
                parsed -> true);
    }

    /**
     * Prefer {@link #collectMergedJsonForTypes}. Kept for callers that still pass a conventional
     * subdir name; behavior is type-based across the whole {@code load/} tree.
     */
    public static Map<Identifier, JsonElement> collectMergedJsonForSubdir(
            ResourceManager resourceManager,
            String subdirUnderLoad,
            Set<String> acceptedTypes) {
        return collectMergedJsonForTypes(resourceManager, acceptedTypes);
    }

    public static Map<Identifier, JsonElement> collectFromModJar(
            String modId,
            String dataNamespace,
            String subdirUnderLoad) {
        return collectFromModJarPath(modId, dataNamespace, LOAD_FOLDER + "/" + subdirUnderLoad);
    }

    /**
     * Bootstrap JSON from {@code data/<dataNamespace>/<relativeUnderData>/} inside {@code modId}'s jar.
     */
    public static Map<Identifier, JsonElement> collectFromModJarPath(
            String modId,
            String dataNamespace,
            String relativeUnderData) {
        Map<Identifier, JsonElement> out = new LinkedHashMap<>();
        String dirInRoot = "data/" + dataNamespace + "/" + relativeUnderData;
        ModList.get().getModContainerById(modId).ifPresent(container -> {
            var owning = container.getModInfo().getOwningFile();
            if (owning == null) {
                return;
            }
            Path root = owning.getFile().getFilePath();
            try {
                if (Files.isDirectory(root)) {
                    Path buildDir = root.getParent() != null && root.getParent().getParent() != null
                            ? root.getParent().getParent().getParent()
                            : null;
                    Path resources = buildDir != null ? buildDir.resolve("resources").resolve("main") : null;
                    Path base = resources != null && Files.exists(resources.resolve(dirInRoot))
                            ? resources.resolve(dirInRoot)
                            : root.resolve(dirInRoot);
                    if (Files.exists(base)) {
                        try (Stream<Path> walk = Files.walk(base)) {
                            walk.filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".json"))
                                    .sorted()
                                    .forEach(file -> readOne(out, base, file, dataNamespace, relativeUnderData));
                        }
                    }
                } else {
                    try (var fs = FileSystems.newFileSystem(root)) {
                        Path base = fs.getPath(dirInRoot);
                        if (Files.exists(base)) {
                            try (Stream<Path> walk = Files.walk(base)) {
                                walk.filter(Files::isRegularFile)
                                        .filter(p -> p.toString().endsWith(".json"))
                                        .sorted()
                                        .forEach(file -> readOne(out, base, file, dataNamespace, relativeUnderData));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to bootstrap {} from {}: {}", relativeUnderData, modId, e.getMessage());
            }
        });
        return out;
    }

    public static List<Map.Entry<Identifier, JsonElement>> orderedEntries(Map<Identifier, JsonElement> merged) {
        List<Map.Entry<Identifier, JsonElement>> list = new ArrayList<>(merged.entrySet());
        list.sort(Comparator
                .comparingInt((Map.Entry<Identifier, JsonElement> e) -> namespacePriority(e.getKey().getNamespace()))
                .thenComparing(e -> e.getKey().toString()));
        return list;
    }

    public static String definitionIdFromLocation(Identifier location) {
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.endsWith(".json")) {
            return file.substring(0, file.length() - 5);
        }
        return file;
    }

    private static Map<Identifier, JsonElement> collectFromStacks(
            ResourceManager resourceManager,
            String listRoot,
            Predicate<Identifier> locationFilter,
            Predicate<JsonElement> keep) {
        Map<Identifier, JsonElement> out = new LinkedHashMap<>();
        Map<Identifier, List<Resource>> stacks = resourceManager.listResourceStacks(listRoot, locationFilter);
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            Resource top = stack.get(stack.size() - 1);
            try (var reader = new BufferedReader(new InputStreamReader(top.open(), StandardCharsets.UTF_8))) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null && keep.test(parsed)) {
                    out.put(entry.getKey(), parsed);
                }
            } catch (IOException | JsonParseException ex) {
                LOGGER.error("Failed to read load JSON {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    private static int namespacePriority(String namespace) {
        if ("iska_lib".equals(namespace)) {
            return 0;
        }
        if ("iska_utils".equals(namespace)) {
            return 1;
        }
        return 2;
    }

    private static boolean isUnderLoadTree(Identifier id) {
        String p = id.getPath();
        return p.startsWith(LOAD_FOLDER + "/") && p.endsWith(".json");
    }

    private static boolean typeMatches(JsonElement element, Set<String> acceptedTypes) {
        if (acceptedTypes == null || acceptedTypes.isEmpty() || element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) {
            return false;
        }
        return acceptedTypes.contains(obj.get("type").getAsString());
    }

    private static void readOne(
            Map<Identifier, JsonElement> out,
            Path base,
            Path file,
            String dataNamespace,
            String relativeUnderData) {
        try {
            String relative = base.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".json")) {
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath(
                    dataNamespace, relativeUnderData + "/" + relative);
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null) {
                    out.put(id, element);
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Failed to read {}: {}", file, ex.getMessage());
        }
    }
}
