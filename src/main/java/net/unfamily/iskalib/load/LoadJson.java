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
import java.util.stream.Stream;

/**
 * Collects JSON under {@code data/<namespace>/load/…} for Library-owned catalogs.
 */
public final class LoadJson {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadJson.class);
    private static final Gson GSON = new Gson();
    public static final String LOAD_FOLDER = "load";

    private LoadJson() {}

    public static Map<Identifier, JsonElement> collectMergedJsonForSubdir(
            ResourceManager resourceManager,
            String subdirUnderLoad,
            Set<String> acceptedTypes) {
        Map<Identifier, JsonElement> out = new LinkedHashMap<>();
        String prefix = LOAD_FOLDER + "/" + subdirUnderLoad + "/";
        Map<Identifier, List<Resource>> stacks = resourceManager.listResourceStacks(
                LOAD_FOLDER,
                id -> id.getPath().endsWith(".json")
                        && (id.getPath().startsWith(prefix) || isFlatLoadJson(id)));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            Resource top = stack.get(stack.size() - 1);
            try (var reader = new BufferedReader(new InputStreamReader(top.open(), StandardCharsets.UTF_8))) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed == null) {
                    continue;
                }
                Identifier id = entry.getKey();
                if (id.getPath().startsWith(prefix) || typeMatches(parsed, acceptedTypes)) {
                    out.put(id, parsed);
                }
            } catch (IOException | JsonParseException ex) {
                LOGGER.error("Failed to read load JSON {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    public static Map<Identifier, JsonElement> collectFromModJar(
            String modId,
            String dataNamespace,
            String subdirUnderLoad) {
        Map<Identifier, JsonElement> out = new LinkedHashMap<>();
        String dirInRoot = "data/" + dataNamespace + "/" + LOAD_FOLDER + "/" + subdirUnderLoad;
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
                                    .forEach(file -> readOne(out, base, file, dataNamespace, subdirUnderLoad));
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
                                        .forEach(file -> readOne(out, base, file, dataNamespace, subdirUnderLoad));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to bootstrap load/{} from {}: {}", subdirUnderLoad, modId, e.getMessage());
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

    private static int namespacePriority(String namespace) {
        if ("iska_lib".equals(namespace)) {
            return 0;
        }
        if ("iska_utils".equals(namespace)) {
            return 1;
        }
        return 2;
    }

    private static boolean isFlatLoadJson(Identifier id) {
        String p = id.getPath();
        if (!p.startsWith(LOAD_FOLDER + "/") || !p.endsWith(".json")) {
            return false;
        }
        String after = p.substring(LOAD_FOLDER.length() + 1);
        return !after.contains("/");
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
            String subdirUnderLoad) {
        try {
            String relative = base.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".json")) {
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath(
                    dataNamespace, LOAD_FOLDER + "/" + subdirUnderLoad + "/" + relative);
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
