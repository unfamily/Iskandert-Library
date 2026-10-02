package net.unfamily.iskalib.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.load.LoadFilesystemBootstrap;
import net.unfamily.iskalib.load.LoadJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool JSON is <strong>startup</strong>: creates Library items {@code iska_lib:<id>} and AOE behavior.
 * {@code /reload} only refreshes behavior overlays for items already registered (new ids need a restart).
 * <p>
 * Java {@link #register} binds AOE to any existing item id (any mod) without creating an item.
 */
public final class ToolBehaviorLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolBehaviorLoader.class);

    public static final String TYPE_TOOLS = "iska_lib:tools";
    public static final Set<String> ACCEPTED_TYPES = Set.of(TYPE_TOOLS);

    public static final String LOAD_SUBDIR = "iska_lib_tools";
    public static final String SIDECAR_DIR = "iska_lib/tools";

    private static final Map<ResourceLocation, ToolBehaviorDefinition> FROM_JSON = new HashMap<>();
    private static final Map<ResourceLocation, ToolBehaviorDefinition> FROM_JAVA = new HashMap<>();

    private ToolBehaviorLoader() {}

    /** Startup only: scan disk/jar, register {@code iska_lib} items, seed behaviors. */
    public static void loadAllBootstrap() {
        FROM_JSON.clear();
        Map<ResourceLocation, JsonElement> merged = collectMerged(null);
        for (var e : LoadJson.orderedEntries(merged)) {
            parseRoot(e.getKey().toString(), e.getValue(), true);
        }
        LOGGER.info("Tool startup: {} library item(s), {} java binding(s)", FROM_JSON.size(), FROM_JAVA.size());
    }

    /**
     * Datapack reload: refresh AOE overlays for already-registered Library tools / Java bindings.
     * Does <strong>not</strong> create new registry items.
     */
    public static void loadAll(ResourceManager resourceManager) {
        Map<ResourceLocation, ToolBehaviorDefinition> previous = new HashMap<>(FROM_JSON);
        FROM_JSON.clear();
        Map<ResourceLocation, JsonElement> merged = collectMerged(resourceManager);
        for (var e : LoadJson.orderedEntries(merged)) {
            parseRoot(e.getKey().toString(), e.getValue(), false);
        }
        for (ResourceLocation id : previous.keySet()) {
            if (!FROM_JSON.containsKey(id) && IskaLibTools.isRegistered(id)) {
                FROM_JSON.put(id, previous.get(id));
            }
        }
        LOGGER.info("Tool reload overlays: {} json, {} java (new item ids still need a restart)",
                FROM_JSON.size(), FROM_JAVA.size());
    }

    private static Map<ResourceLocation, JsonElement> collectMerged(ResourceManager resourceManagerOrNull) {
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
        if (resourceManagerOrNull != null) {
            merged.putAll(LoadJson.collectMergedJsonForTypes(resourceManagerOrNull, ACCEPTED_TYPES));
            merged.putAll(LoadJson.collectMergedJsonUnderLoadSubdir(resourceManagerOrNull, LOAD_SUBDIR));
            merged.putAll(LoadJson.collectMergedJsonUnderDataDir(resourceManagerOrNull, SIDECAR_DIR));
        } else {
            merged.putAll(LoadJson.collectFromModJar("iska_lib", "iska_lib", LOAD_SUBDIR));
            merged.putAll(LoadJson.collectFromModJarPath("iska_lib", "iska_lib", SIDECAR_DIR));
            int external = LoadFilesystemBootstrap.mergeIntoForTypes(merged, ACCEPTED_TYPES);
            if (external > 0) {
                LOGGER.info("Tool JSON bootstrap: merged {} file(s) from configured datapack paths", external);
            }
        }
        return merged;
    }

    public static void register(
            ResourceLocation itemId,
            ToolBehaviorType behavior,
            int range,
            List<TagKey<Block>> harvestTags,
            int durability) {
        if (itemId == null || behavior == null) {
            return;
        }
        FROM_JAVA.put(itemId, new ToolBehaviorDefinition(itemId, behavior, range, harvestTags, durability));
    }

    public static void register(ResourceLocation itemId, ToolBehaviorType behavior, int range, List<TagKey<Block>> harvestTags) {
        register(itemId, behavior, range, harvestTags, ToolBehaviorDefinition.DURABILITY_VANILLA);
    }

    public static void register(ResourceLocation itemId, ToolBehaviorType behavior, int range) {
        register(itemId, behavior, range, List.of());
    }

    public static void register(ResourceLocation itemId, ToolBehaviorType behavior, int range, int durability) {
        register(itemId, behavior, range, List.of(), durability);
    }

    public static void unregister(ResourceLocation itemId) {
        if (itemId != null) {
            FROM_JAVA.remove(itemId);
        }
    }

    public static ToolBehaviorDefinition getForItem(ResourceLocation itemId) {
        if (itemId == null) {
            return null;
        }
        ToolBehaviorDefinition javaDef = FROM_JAVA.get(itemId);
        if (javaDef != null) {
            return javaDef;
        }
        return FROM_JSON.get(itemId);
    }

    public static Map<ResourceLocation, ToolBehaviorDefinition> getAllByItem() {
        Map<ResourceLocation, ToolBehaviorDefinition> out = new LinkedHashMap<>(FROM_JSON);
        out.putAll(FROM_JAVA);
        return Map.copyOf(out);
    }

    private static void parseRoot(String source, JsonElement root, boolean registerItems) {
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()
                || !ACCEPTED_TYPES.contains(obj.get("type").getAsString())) {
            LOGGER.warn("Skipping {}: expected type {}", source, TYPE_TOOLS);
            return;
        }
        if (obj.has("tools") && obj.get("tools").isJsonArray()) {
            for (JsonElement entry : obj.getAsJsonArray("tools")) {
                if (entry.isJsonObject()) {
                    parseEntry(source, entry.getAsJsonObject(), registerItems);
                }
            }
            return;
        }
        if (obj.has("id")) {
            parseEntry(source, obj, registerItems);
        }
    }

    private static void parseEntry(String source, JsonObject json, boolean registerItems) {
        try {
            String idPath = resolveIdPath(requiredString(json, "id"));
            ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(IskaLib.MOD_ID, idPath);
            String behaviorRaw = requiredString(json, "behavior");
            ToolBehaviorType behavior = ToolBehaviorType.fromString(behaviorRaw)
                    .orElseThrow(() -> new IllegalArgumentException("unknown behavior: " + behaviorRaw));
            int range = json.has("range") ? json.get("range").getAsInt() : 1;
            int durability = json.has("durability") && json.get("durability").isJsonPrimitive()
                    ? json.get("durability").getAsInt()
                    : ToolBehaviorDefinition.DURABILITY_VANILLA;
            List<TagKey<Block>> tags = parseHarvestTags(json);
            ToolBehaviorDefinition def = new ToolBehaviorDefinition(itemId, behavior, range, tags, durability);

            if (registerItems) {
                IskaLibTools.registerLibraryTool(def);
                FROM_JSON.put(itemId, def);
            } else if (IskaLibTools.isRegistered(itemId)) {
                FROM_JSON.put(itemId, def);
            } else {
                LOGGER.warn(
                        "Tool JSON {} id {} is new; item registration is startup-only — restart the game to create iska_lib:{}",
                        source, idPath, idPath);
            }
        } catch (Exception ex) {
            LOGGER.warn("Invalid tool behavior in {}: {}", source, ex.getMessage());
        }
    }

    private static String resolveIdPath(String raw) {
        String id = raw.trim();
        if (id.contains(":")) {
            ResourceLocation parsed = ResourceLocation.parse(id);
            if (!IskaLib.MOD_ID.equals(parsed.getNamespace())) {
                throw new IllegalArgumentException(
                        "JSON tool id must be path-only or iska_lib:<path> (got " + id + "); other modids are Java-only");
            }
            return parsed.getPath();
        }
        return id;
    }

    private static List<TagKey<Block>> parseHarvestTags(JsonObject json) {
        if (!json.has("harvest_tags") || !json.get("harvest_tags").isJsonArray()) {
            return List.of();
        }
        JsonArray array = json.getAsJsonArray("harvest_tags");
        List<TagKey<Block>> tags = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonPrimitive()) {
                continue;
            }
            String raw = el.getAsString();
            if (raw.isBlank()) {
                continue;
            }
            ResourceLocation tagId = raw.startsWith("#")
                    ? ResourceLocation.parse(raw.substring(1))
                    : ResourceLocation.parse(raw);
            tags.add(TagKey.create(Registries.BLOCK, tagId));
        }
        return tags;
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing field: " + field);
        }
        String value = json.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("empty field: " + field);
        }
        return value;
    }
}
