package net.unfamily.iskalib.structure;

import com.google.gson.JsonObject;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Integration point for structure definitions and client-side saved structures.
 */
public final class StructureIOHooks {
    private StructureIOHooks() {}

    @FunctionalInterface
    public interface StructureDefinitionSink {
        void accept(String definitionId, String filePathForLog, JsonObject json);
    }

    public interface Listener {
        /**
         * Loads server structure JSON from datapacks (path {@code load/iska_utils_structure_definitions/} per namespace)
         * and/or mod bootstrap. When {@code resourceManager} is null (early game load), implementations should
         * supply built-in definitions only (e.g. from the mod jar).
         */
        void loadServerStructureDefinitions(ResourceManager resourceManager, StructureDefinitionSink sink);

        /**
         * Whether client-side personal structures are accepted.
         */
        boolean acceptClientStructures();

        /**
         * Optional extra search path for client structures (relative or absolute).
         */
        String clientStructurePath();

        /**
         * If false, client structures cannot enable "place like player".
         */
        boolean allowClientStructurePlaceLikePlayer();
    }

    private static volatile Listener listener = new Listener() {
        @Override
        public void loadServerStructureDefinitions(ResourceManager resourceManager, StructureDefinitionSink sink) {
            // No definitions unless a consumer registers a real listener
        }

        @Override
        public boolean acceptClientStructures() {
            return false;
        }

        @Override
        public String clientStructurePath() {
            return "";
        }

        @Override
        public boolean allowClientStructurePlaceLikePlayer() {
            return false;
        }
    };

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener != null ? newListener : listener;
    }
}
