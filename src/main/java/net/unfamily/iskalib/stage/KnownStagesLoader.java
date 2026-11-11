package net.unfamily.iskalib.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.unfamily.iskalib.load.LoadJson;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads known stage ids for Tab suggestions from any {@code data/<ns>/load/**} file whose
 * {@code type} is {@code iska_lib:known_stages} or {@code iska_utils:known_stages}.
 */
public final class KnownStagesLoader {
    public static final String TYPE_KNOWN_STAGES = "iska_lib:known_stages";
    public static final String TYPE_KNOWN_STAGES_UTILS = "iska_utils:known_stages";
    private static final Set<String> ACCEPTED_TYPES = Set.of(TYPE_KNOWN_STAGES, TYPE_KNOWN_STAGES_UTILS);

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(KnownStagesLoader.class);

    /** stage_type → ids (cleared on each load). */
    private static final Map<String, Set<String>> BY_TYPE = new LinkedHashMap<>();
    private static final StageCatalog.Contributor CONTRIBUTOR = new StageCatalog.Contributor() {
        @Override
        public Collection<String> knownStages() {
            LinkedHashSet<String> all = new LinkedHashSet<>();
            for (Set<String> set : BY_TYPE.values()) {
                all.addAll(set);
            }
            return all;
        }

        @Override
        public Collection<String> knownStagesForType(String stageType) {
            String type = StageCatalog.normalizeStageType(stageType);
            if (type == null) {
                return List.of();
            }
            Set<String> set = BY_TYPE.get(type);
            return set == null ? List.of() : set;
        }
    };

    private static boolean contributorInstalled;

    private KnownStagesLoader() {}

    public static void ensureContributor() {
        if (!contributorInstalled) {
            StageCatalog.addContributor(CONTRIBUTOR);
            contributorInstalled = true;
        }
    }

    public static void loadAll(@Nullable ResourceManager resourceManagerOrNull) {
        ensureContributor();
        LOGGER.info("Loading known stages (type match under load/) ...");
        BY_TYPE.clear();
        try {
            Map<ResourceLocation, JsonElement> merged = resourceManagerOrNull != null
                    ? LoadJson.collectMergedJsonForTypes(resourceManagerOrNull, ACCEPTED_TYPES)
                    : Map.of();
            for (var e : LoadJson.orderedEntries(merged)) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                parseConfigJson(e.getKey().toString(), e.getValue().getAsJsonObject());
            }
            int count = BY_TYPE.values().stream().mapToInt(Set::size).sum();
            LOGGER.info("Known stages loaded: {} id(s) across {} type(s)", count, BY_TYPE.size());
        } catch (Exception e) {
            LOGGER.error("Error loading known stages: {}", e.getMessage());
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
                LOGGER.warn("Unsupported type '{}' in file {}. Expected known_stages type", type, filePath);
                return;
            }
            if (!json.has("stages") || !json.get("stages").isJsonArray()) {
                LOGGER.warn("File {} does not have valid 'stages' array", filePath);
                return;
            }
            JsonArray stages = json.getAsJsonArray("stages");
            for (int i = 0; i < stages.size(); i++) {
                JsonElement elem = stages.get(i);
                if (!elem.isJsonObject()) {
                    LOGGER.warn("Element {} in 'stages' is not an object in {}", i, filePath);
                    continue;
                }
                JsonObject obj = elem.getAsJsonObject();
                String stageType = obj.has("stage_type") ? obj.get("stage_type").getAsString() : null;
                String stage = obj.has("stage") ? obj.get("stage").getAsString() : null;
                String normalized = StageCatalog.normalizeStageType(stageType);
                if (normalized == null || stage == null || stage.isBlank()) {
                    LOGGER.warn("Skipping known stage #{} in {}: need stage_type (player|world|team) and stage",
                            i, filePath);
                    continue;
                }
                BY_TYPE.computeIfAbsent(normalized, t -> new LinkedHashSet<>()).add(stage.trim());
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing known stages from {}: {}", filePath, e.getMessage());
        }
    }

    public static Map<String, Set<String>> snapshotByType() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (var e : BY_TYPE.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }
}
