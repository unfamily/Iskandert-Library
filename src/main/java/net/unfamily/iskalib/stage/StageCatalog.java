package net.unfamily.iskalib.stage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
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
    }

    private static final CopyOnWriteArrayList<Contributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();
    private static final Set<String> STATIC_STAGES = Collections.synchronizedSet(new LinkedHashSet<>());

    public static void addContributor(Contributor contributor) {
        if (contributor != null) {
            CONTRIBUTORS.addIfAbsent(contributor);
        }
    }

    public static void removeContributor(Contributor contributor) {
        CONTRIBUTORS.remove(contributor);
    }

    /** Registers a stage id that should always appear in suggestions. */
    public static void registerKnownStage(String stage) {
        if (stage != null && !stage.isBlank()) {
            STATIC_STAGES.add(stage.trim());
        }
    }

    public static void registerKnownStages(Collection<String> stages) {
        if (stages == null) {
            return;
        }
        for (String stage : stages) {
            registerKnownStage(stage);
        }
    }

    public static void clearStaticStages() {
        STATIC_STAGES.clear();
    }

    /**
     * Union of static registrations and all contributor ids.
     */
    public static Set<String> getKnownStages() {
        LinkedHashSet<String> out = new LinkedHashSet<>(STATIC_STAGES);
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
}
