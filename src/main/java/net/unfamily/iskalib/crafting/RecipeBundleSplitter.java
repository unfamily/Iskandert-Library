package net.unfamily.iskalib.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Splits datapack recipe JSON bundles ({@code recipes}/{@code sources}/{@code entries}) into
 * one RecipeManager entry per array element, before KubeJS/CraftTweaker see the map.
 *
 * <p>Child id is always {@code <ns>:<path>_<index>} (continuous index). Optional entry
 * {@code "id"} is left in JSON as metadata only and is not used for the ResourceLocation.
 *
 * <p>Also injects KubeJS-friendly {@code results} / {@code ingredients} mirrors so generic
 * remove/replace filters can match custom select/require/produce layouts.
 */
public final class RecipeBundleSplitter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<String> BUNDLE_KEYS = List.of("recipes", "sources", "entries");

    private static final Set<String> TARGET_TYPES = ConcurrentHashMap.newKeySet();

    static {
        registerType("iska_utils:factory");
        registerType("iska_utils:factory_sources");
        registerType("iska_utils:ancient_tab");
    }

    private RecipeBundleSplitter() {}

    /** Register a recipe JSON {@code type} string that may contain bundle arrays. */
    public static void registerType(String typeId) {
        if (typeId != null && !typeId.isBlank()) {
            TARGET_TYPES.add(typeId.trim());
        }
    }

    public static Set<String> registeredTypes() {
        return Set.copyOf(TARGET_TYPES);
    }

    /**
     * Mutates {@code recipeJsons} in place: removes bundle parents and inserts split children.
     *
     * @param parseKey parses {@code namespace:path} into the map key type
     * @param keyToString string form of an existing key (used as base path)
     */
    public static <K> void splitInPlace(
            Map<K, JsonElement> recipeJsons,
            Function<String, K> parseKey,
            Function<K, String> keyToString) {
        Objects.requireNonNull(recipeJsons, "recipeJsons");
        Objects.requireNonNull(parseKey, "parseKey");
        Objects.requireNonNull(keyToString, "keyToString");

        List<Map.Entry<K, JsonElement>> snapshot = new ArrayList<>(recipeJsons.entrySet());
        int splitFiles = 0;
        int childCount = 0;

        for (Map.Entry<K, JsonElement> entry : snapshot) {
            JsonElement raw = entry.getValue();
            if (raw == null || !raw.isJsonObject()) {
                continue;
            }
            JsonObject root = raw.getAsJsonObject();
            if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
                continue;
            }
            String type = root.get("type").getAsString();
            if (!TARGET_TYPES.contains(type)) {
                continue;
            }

            List<IndexedEntry> bundled = collectBundledEntries(root);
            if (bundled.isEmpty()) {
                injectKubeMatchFields(root);
                continue;
            }

            K parentKey = entry.getKey();
            String parentId = keyToString.apply(parentKey);
            recipeJsons.remove(parentKey);
            splitFiles++;

            for (IndexedEntry indexed : bundled) {
                String childId = parentId + "_" + indexed.index();
                K childKey;
                try {
                    childKey = parseKey.apply(childId);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Recipe bundle split: invalid child id {} from {}", childId, parentId);
                    continue;
                }
                if (recipeJsons.containsKey(childKey)) {
                    LOGGER.warn("Recipe bundle split: child id {} already exists; skipping", childId);
                    continue;
                }
                JsonObject child = indexed.entry().deepCopy();
                child.addProperty("type", type);
                injectKubeMatchFields(child);
                recipeJsons.put(childKey, child);
                childCount++;
            }
        }

        if (splitFiles > 0) {
            LOGGER.info(
                    "Recipe bundle split: {} file(s) → {} single recipe(s) (types={})",
                    splitFiles,
                    childCount,
                    TARGET_TYPES);
        }
    }

    private static List<IndexedEntry> collectBundledEntries(JsonObject root) {
        List<IndexedEntry> out = new ArrayList<>();
        boolean anyBundle = false;
        int continuousIndex = 0;
        for (String key : BUNDLE_KEYS) {
            if (!root.has(key) || !root.get(key).isJsonArray()) {
                continue;
            }
            anyBundle = true;
            JsonArray array = root.getAsJsonArray(key);
            for (JsonElement el : array) {
                if (el != null && el.isJsonObject()) {
                    out.add(new IndexedEntry(continuousIndex, el.getAsJsonObject()));
                }
                continuousIndex++;
            }
        }
        if (!anyBundle) {
            return List.of();
        }
        return out;
    }

    /**
     * Adds {@code results} (item ids) and {@code ingredients} (item/tag ids) for KubeJS matching.
     * Leaves native fields ({@code select}, {@code produce}, {@code require}, {@code input}) intact.
     */
    static void injectKubeMatchFields(JsonObject child) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        LinkedHashSet<String> ingredients = new LinkedHashSet<>();

        collectOutputIds(child, results);
        if (child.has("if") && child.get("if").isJsonArray()) {
            for (JsonElement branchEl : child.getAsJsonArray("if")) {
                if (branchEl != null && branchEl.isJsonObject()) {
                    JsonObject branch = branchEl.getAsJsonObject();
                    // Factory if[] payloads often nest select under the branch object.
                    collectOutputIds(branch, results);
                    if (branch.has("then") && branch.get("then").isJsonObject()) {
                        collectOutputIds(branch.getAsJsonObject("then"), results);
                    }
                }
            }
        }

        if (child.has("input") && child.get("input").isJsonPrimitive()) {
            String input = child.get("input").getAsString().trim();
            if (!input.isEmpty()) {
                ingredients.add(input);
            }
        }
        collectRequireIds(child, ingredients);

        if (!results.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String id : results) {
                arr.add(itemStackJson(id));
            }
            child.add("results", arr);
        }
        if (!ingredients.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String id : ingredients) {
                arr.add(ingredientJson(id));
            }
            child.add("ingredients", arr);
        }
    }

    /** KubeJS {@code item_stack} expects an object (not a bare string). */
    private static JsonObject itemStackJson(String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.startsWith("#") ? id.substring(1) : id);
        obj.addProperty("count", 1);
        return obj;
    }

    /** KubeJS {@code ingredient}: item id string, or tag object. */
    private static JsonElement ingredientJson(String id) {
        if (id.startsWith("#")) {
            JsonObject obj = new JsonObject();
            obj.addProperty("tag", id.substring(1));
            return obj;
        }
        return new com.google.gson.JsonPrimitive(id);
    }

    private static void collectOutputIds(JsonObject obj, Set<String> out) {
        collectSelectLike(obj, "select", out);
        collectSelectLike(obj, "colors", out);
        if (obj.has("produce")) {
            collectProduce(obj.get("produce"), out);
        }
    }

    private static void collectSelectLike(JsonObject obj, String key, Set<String> out) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement el : obj.getAsJsonArray(key)) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject row = el.getAsJsonObject();
            String id = null;
            if (row.has("output") && row.get("output").isJsonPrimitive()) {
                id = row.get("output").getAsString();
            } else if (row.has("id") && row.get("id").isJsonPrimitive()) {
                id = row.get("id").getAsString();
            }
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
    }

    private static void collectProduce(JsonElement produce, Set<String> out) {
        if (produce == null) {
            return;
        }
        if (produce.isJsonPrimitive()) {
            String id = produce.getAsString().trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
            return;
        }
        if (produce.isJsonArray()) {
            for (JsonElement el : produce.getAsJsonArray()) {
                collectProduce(el, out);
            }
            return;
        }
        if (produce.isJsonObject()) {
            JsonObject obj = produce.getAsJsonObject();
            if (obj.has("id") && obj.get("id").isJsonPrimitive()) {
                String id = obj.get("id").getAsString().trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            } else if (obj.has("item") && obj.get("item").isJsonPrimitive()) {
                String id = obj.get("item").getAsString().trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
    }

    private static void collectRequireIds(JsonObject child, Set<String> out) {
        if (!child.has("require") || !child.get("require").isJsonArray()) {
            return;
        }
        for (JsonElement el : child.getAsJsonArray("require")) {
            if (el == null) {
                continue;
            }
            if (el.isJsonPrimitive()) {
                String id = el.getAsString().trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
                continue;
            }
            if (el.isJsonObject()) {
                JsonObject row = el.getAsJsonObject();
                if (row.has("id") && row.get("id").isJsonPrimitive()) {
                    String id = row.get("id").getAsString().trim();
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                } else if (row.has("item") && row.get("item").isJsonPrimitive()) {
                    String id = row.get("item").getAsString().trim();
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                } else if (row.has("tag") && row.get("tag").isJsonPrimitive()) {
                    String tag = row.get("tag").getAsString().trim();
                    if (!tag.isEmpty()) {
                        out.add(tag.startsWith("#") ? tag : "#" + tag);
                    }
                }
            }
        }
    }

    private record IndexedEntry(int index, JsonObject entry) {}
}
