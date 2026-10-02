package net.unfamily.iskalib.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
 * AOE tool behaviors: bind an item id to lumberjack / excavator / scythe / paxel.
 * <p>
 * JSON: {@code "id"} path only; always registered as {@code iska_lib:<id>}. No {@code item} field.
 * Optional {@code durability}: {@code -1} = infinite AOE extras, {@code >= 0} = flat damage once for extras.
 * <p>
 * Java: {@link #register} may use any hosting-mod item id.
 */
public final class ToolBehaviorLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolBehaviorLoader.class);

    public static final String TYPE_TOOLS = "iska_lib:tools";
    public static final Set<String> ACCEPTED_TYPES = Set.of(TYPE_TOOLS);

    public static final String LOAD_SUBDIR = "iska_lib_tools";
    public static final String SIDECAR_DIR = "iska_lib/tools";

    private static final Map<Identifier, ToolBehaviorDefinition> FROM_JSON = new HashMap<>();
    private static final Map<Identifier, ToolBehaviorDefinition> FROM_JAVA = new HashMap<>();

    private ToolBehaviorLoader() {}

    public static void loadAll(ResourceManager resourceManagerOrNull) {
        FROM_JSON.clear();
        Map<Identifier, JsonElement> merged = new LinkedHashMap<>();
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
        for (var e : LoadJson.orderedEntries(merged)) {
            parseRoot(e.getKey().toString(), e.getValue());
        }
        LOGGER.info("Tool behavior configurations loaded: {} json, {} java", FROM_JSON.size(), FROM_JAVA.size());
    }

    public static void loadAllBootstrap() {
        loadAll(null);
    }

    /**
     * Java registration for an item owned by the hosting mod. Survives datapack reload; overrides JSON.
     */
    public static void register(
            Identifier itemId,
            ToolBehaviorType behavior,
            int range,
            List<TagKey<Block>> harvestTags,
            int durability) {
        if (itemId == null || behavior == null) {
            return;
        }
        FROM_JAVA.put(itemId, new ToolBehaviorDefinition(itemId, behavior, range, harvestTags, durability));
    }

    public static void register(Identifier itemId, ToolBehaviorType behavior, int range, List<TagKey<Block>> harvestTags) {
        register(itemId, behavior, range, harvestTags, ToolBehaviorDefinition.DURABILITY_VANILLA);
    }

    public static void register(Identifier itemId, ToolBehaviorType behavior, int range) {
        register(itemId, behavior, range, List.of());
    }

    public static void register(Identifier itemId, ToolBehaviorType behavior, int range, int durability) {
        register(itemId, behavior, range, List.of(), durability);
    }

    public static void unregister(Identifier itemId) {
        if (itemId != null) {
            FROM_JAVA.remove(itemId);
        }
    }

    public static ToolBehaviorDefinition getForItem(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        ToolBehaviorDefinition javaDef = FROM_JAVA.get(itemId);
        if (javaDef != null) {
            return javaDef;
        }
        return FROM_JSON.get(itemId);
    }

    public static Map<Identifier, ToolBehaviorDefinition> getAllByItem() {
        Map<Identifier, ToolBehaviorDefinition> out = new LinkedHashMap<>(FROM_JSON);
        out.putAll(FROM_JAVA);
        return Map.copyOf(out);
    }

    private static void parseRoot(String source, JsonElement root) {
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
                    parseEntry(source, entry.getAsJsonObject());
                }
            }
            return;
        }
        if (obj.has("id")) {
            parseEntry(source, obj);
        }
    }

    private static void parseEntry(String source, JsonObject json) {
        try {
            String idPath = resolveIdPath(requiredString(json, "id"));
            Identifier itemId = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, idPath);
            String behaviorRaw = requiredString(json, "behavior");
            ToolBehaviorType behavior = ToolBehaviorType.fromString(behaviorRaw)
                    .orElseThrow(() -> new IllegalArgumentException("unknown behavior: " + behaviorRaw));
            int range = json.has("range") ? json.get("range").getAsInt() : 1;
            int durability = json.has("durability") && json.get("durability").isJsonPrimitive()
                    ? json.get("durability").getAsInt()
                    : ToolBehaviorDefinition.DURABILITY_VANILLA;
            List<TagKey<Block>> tags = parseHarvestTags(json);
            FROM_JSON.put(itemId, new ToolBehaviorDefinition(itemId, behavior, range, tags, durability));
        } catch (Exception ex) {
            LOGGER.warn("Invalid tool behavior in {}: {}", source, ex.getMessage());
        }
    }

    private static String resolveIdPath(String raw) {
        String id = raw.trim();
        if (id.contains(":")) {
            Identifier parsed = Identifier.parse(id);
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
            Identifier tagId = raw.startsWith("#")
                    ? Identifier.parse(raw.substring(1))
                    : Identifier.parse(raw);
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
