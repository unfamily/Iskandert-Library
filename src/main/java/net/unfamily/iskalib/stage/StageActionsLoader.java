package net.unfamily.iskalib.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.unfamily.iskalib.load.LoadJson;
import net.unfamily.iskalib.load.LoadModGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads stage action definitions from datapack JSON under {@code data/<namespace>/load/iska_lib_stage_actions/}.
 * Files use type {@code iska_utils:stage_actions} and contain actions that run when stages are added/removed.
 */
public class StageActionsLoader {
    public static final String STAGE_ACTIONS_SUBDIR = "iska_utils_stage_actions";
    public static final String TYPE_STAGE_ACTIONS = "iska_utils:stage_actions";
    public static final String TYPE_STAGE_ACTIONS_LIB = "iska_lib:stage_actions";
    private static final java.util.Set<String> ACCEPTED_TYPES = java.util.Set.of(TYPE_STAGE_ACTIONS, TYPE_STAGE_ACTIONS_LIB);

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StageActionsLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<StageActionDefinition> LOADED_ACTIONS = new ArrayList<>();

    public static void loadAll(ResourceManager resourceManagerOrNull) {
        LOGGER.info("Loading stage actions from datapack path load/{} ...", STAGE_ACTIONS_SUBDIR);
        LOADED_ACTIONS.clear();
        try {
            Map<ResourceLocation, JsonElement> merged = resourceManagerOrNull != null
                    ? LoadJson.collectMergedJsonForSubdir(resourceManagerOrNull, STAGE_ACTIONS_SUBDIR, ACCEPTED_TYPES)
                    : collectBootstrap();
            for (var e : LoadJson.orderedEntries(merged)) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                parseConfigJson(e.getKey().toString(), e.getValue().getAsJsonObject());
            }
            LOGGER.info("Stage actions loaded: {}", LOADED_ACTIONS.size());
        } catch (Exception e) {
            LOGGER.error("Error loading stage actions: {}", e.getMessage());
            if (LOGGER.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private static void parseConfigJson(String filePath, JsonObject json) {
        try {
            if (!json.has("type")) {
                LOGGER.warn("File {} has no 'type' field, ignored", filePath);
                return;
            }
            String type = json.get("type").getAsString();
            if (!ACCEPTED_TYPES.contains(type)) {
                LOGGER.warn("Unsupported type '{}' in file {}. Expected stage_actions type", type, filePath);
                return;
            }

            if (!json.has("actions") || !json.get("actions").isJsonArray()) {
                LOGGER.warn("File {} does not have valid 'actions' array", filePath);
                return;
            }

            JsonArray actionsArray = json.getAsJsonArray("actions");
            for (int i = 0; i < actionsArray.size(); i++) {
                JsonElement elem = actionsArray.get(i);
                if (elem.isJsonObject()) {
                    JsonObject actionObj = elem.getAsJsonObject();
                    if (!LoadModGate.shouldIncludeAtLoad(actionObj, LOGGER, filePath + "#" + i)) {
                        continue;
                    }
                    try {
                        StageActionDefinition def = StageActionDefinition.fromJson(actionObj);
                        LOADED_ACTIONS.add(def);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Loaded stage action {} (id={}) from file {}", i, def.getId(), filePath);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Skipping action {} in {}: {}", i, filePath, e.getMessage());
                    }
                } else {
                    LOGGER.warn("Element {} in 'actions' is not a valid JSON object in {}", i, filePath);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error parsing stage actions from {}: {}", filePath, e.getMessage());
        }
    }

    private static Map<ResourceLocation, JsonElement> collectBootstrap() {
        Map<ResourceLocation, JsonElement> merged = new java.util.LinkedHashMap<>();
        merged.putAll(LoadJson.collectFromModJar("iska_lib", "iska_lib", STAGE_ACTIONS_SUBDIR));
        merged.putAll(LoadJson.collectFromModJar("iska_utils", "iska_utils", STAGE_ACTIONS_SUBDIR));
        return merged;
    }

    public static void scanConfigDirectory() {
        loadAll(null);
    }

    /**
     * Gets all loaded stage action definitions
     */
    public static List<StageActionDefinition> getLoadedActions() {
        return new ArrayList<>(LOADED_ACTIONS);
    }

    /**
     * Gets action by id, or null if not found
     */
    public static StageActionDefinition getActionById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (StageActionDefinition def : LOADED_ACTIONS) {
            if (id.equals(def.getId())) return def;
        }
        return null;
    }

    /**
     * Gets all action ids for suggestions
     */
    public static List<String> getActionIds() {
        List<String> ids = new ArrayList<>();
        for (StageActionDefinition def : LOADED_ACTIONS) {
            if (!def.getId().isEmpty()) ids.add(def.getId());
        }
        return ids;
    }

    /**
     * Reloads all stage actions from configuration files
     */
    public static void reloadAllActions() {
        LOGGER.info("Reloading stage actions...");
        scanConfigDirectory();
    }
}
