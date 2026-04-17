package net.unfamily.iskalib.structure;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Integration point for locating external structure definitions on disk.
 *
 * <p>The library does not assume any specific mod id. Consumers can override the base directory and
 * subdirectory name used for structure definitions.
 */
public final class StructureIOHooks {
    private StructureIOHooks() {}

    public interface Listener {
        /**
         * @return base folder where external scripts live (e.g. {@code kubejs/external_scripts}).
         */
        Path externalScriptsBasePath();

        /**
         * @return subfolder that contains structure json definitions.
         */
        String structuresFolderName();

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

        /**
         * Optional hook for generating documentation/readme on scan.
         */
        void generateDocumentationIfEnabled();
    }

    private static volatile Listener listener = new Listener() {
        @Override
        public Path externalScriptsBasePath() {
            return Paths.get("kubejs", "external_scripts");
        }

        @Override
        public String structuresFolderName() {
            return "iska_utils_structures";
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

        @Override
        public void generateDocumentationIfEnabled() {}
    };

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener != null ? newListener : listener;
    }
}

