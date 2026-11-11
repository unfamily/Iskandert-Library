package net.unfamily.iskalib.stage;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Known stage ids contributed by consumers (shop, actions, items, equip gates, …),
 * including stages that are not yet assigned to any player/world/team.
 */
public final class StageCatalog {
    private StageCatalog() {}

    public interface Contributor {
        /**
         * @return stage ids known to this contributor (may be empty)
         */
        Collection<String> knownStages();

        /**
         * Stage ids for a specific {@code stage_type} ({@code player}/{@code world}/{@code team}).
         * Default: empty (typed suggestions must not dump untyped catalogs).
         */
        default Collection<String> knownStagesForType(String stageType) {
            return List.of();
        }
    }

    private static final CopyOnWriteArrayList<Contributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();
    private static final Set<String> STATIC_STAGES = Collections.synchronizedSet(new LinkedHashSet<>());
    /** Code / Kube typed registrations ({@code player}/{@code world}/{@code team}). */
    private static final Map<String, Set<String>> TYPED_STATIC = new ConcurrentHashMap<>();

    public static void addContributor(Contributor contributor) {
        if (contributor != null) {
            CONTRIBUTORS.addIfAbsent(contributor);
        }
    }

    public static void removeContributor(Contributor contributor) {
        CONTRIBUTORS.remove(contributor);
    }

    /** Registers a stage id that should always appear in untyped suggestions. */
    public static void registerKnownStage(String stage) {
        if (stage != null && !stage.isBlank()) {
            STATIC_STAGES.add(stage.trim());
        }
    }

    /**
     * Typed registration for command Tab suggestions ({@code player}/{@code world}/{@code team}).
     * {@code team_player} is normalized to {@code team}.
     */
    public static void registerKnownStage(@Nullable String stageType, @Nullable String stage) {
        if (stage == null || stage.isBlank()) {
            return;
        }
        String id = stage.trim();
        STATIC_STAGES.add(id);
        String type = normalizeStageType(stageType);
        if (type == null) {
            return;
        }
        TYPED_STATIC.computeIfAbsent(type, t -> Collections.synchronizedSet(new LinkedHashSet<>())).add(id);
    }

    public static void registerKnownStages(Collection<String> stages) {
        if (stages == null) {
            return;
        }
        for (String stage : stages) {
            registerKnownStage(stage);
        }
    }

    public static void registerKnownStages(@Nullable String stageType, Collection<String> stages) {
        if (stages == null) {
            return;
        }
        for (String stage : stages) {
            registerKnownStage(stageType, stage);
        }
    }

    public static void clearStaticStages() {
        STATIC_STAGES.clear();
        TYPED_STATIC.clear();
    }

    /** Clears only typed static registrations (not untyped STATIC_STAGES / contributors). */
    public static void clearTypedStaticStages() {
        TYPED_STATIC.clear();
    }

    /**
     * Union of static registrations and all contributor ids.
     */
    public static Set<String> getKnownStages() {
        LinkedHashSet<String> out = new LinkedHashSet<>(STATIC_STAGES);
        for (Set<String> typed : TYPED_STATIC.values()) {
            out.addAll(typed);
        }
        for (Contributor contributor : CONTRIBUTORS) {
            try {
                Collection<String> known = contributor.knownStages();
                if (known != null) {
                    for (String stage : known) {
                        if (stage != null && !stage.isBlank()) {
                            out.add(stage.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Consumer failure must not break suggestions
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Known stage ids for one command branch ({@code player}/{@code world}/{@code team}).
     * Includes typed static registrations and typed contributor ids (not untyped dump).
     */
    public static Set<String> getKnownStages(@Nullable String stageType) {
        if (stageType == null || stageType.isBlank()) {
            return getKnownStages();
        }
        String type = normalizeStageType(stageType);
        if (type == null) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Set<String> typed = TYPED_STATIC.get(type);
        if (typed != null) {
            out.addAll(typed);
        }
        for (Contributor contributor : CONTRIBUTORS) {
            try {
                Collection<String> known = contributor.knownStagesForType(type);
                if (known != null) {
                    for (String stage : known) {
                        if (stage != null && !stage.isBlank()) {
                            out.add(stage.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Consumer failure must not break suggestions
            }
        }
        return Collections.unmodifiableSet(out);
    }

    @Nullable
    public static String normalizeStageType(@Nullable String stageType) {
        if (stageType == null || stageType.isBlank()) {
            return null;
        }
        String type = stageType.trim().toLowerCase(Locale.ROOT);
        if ("team_player".equals(type)) {
            return "team";
        }
        if ("player".equals(type) || "world".equals(type) || "team".equals(type)) {
            return type;
        }
        return null;
    }
}
