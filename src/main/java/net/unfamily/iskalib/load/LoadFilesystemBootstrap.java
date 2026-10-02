package net.unfamily.iskalib.load;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import net.unfamily.iskalib.IskaLibConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads {@code data/<ns>/load/…} (and sidecar dirs) from disk before a {@link
 * net.minecraft.server.packs.resources.ResourceManager} exists.
 * <p>
 * Roots come from {@link IskaLibConfig#BOOTSTRAP_DATAPACK_PATHS} (KubeJS data root, Open Loader,
 * Global Packs, instance datapacks). Pack folders and {@code .zip}/{@code .jar} are read in-place
 * without extraction.
 */
public final class LoadFilesystemBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFilesystemBootstrap.class);
    private static final Gson GSON = new Gson();

    private LoadFilesystemBootstrap() {}

    /** Merges every external {@code load/} JSON whose root {@code type} is in {@code acceptedTypes}. */
    public static int mergeIntoForTypes(Map<Identifier, JsonElement> target, Set<String> acceptedTypes) {
        if (acceptedTypes == null || acceptedTypes.isEmpty()) {
            return 0;
        }
        return scanConfiguredRoots((dataRoot) -> scanDataRootForTypes(dataRoot, acceptedTypes, target));
    }

    /** Convenience for a single type. */
    public static int mergeIntoForType(Map<Identifier, JsonElement> target, String jsonType) {
        return mergeIntoForTypes(target, Set.of(jsonType));
    }

    /**
     * Merges JSON under {@code data/<ns>/load/<subdirUnderLoad>/…}.
     */
    public static int mergeIntoLoadSubdir(Map<Identifier, JsonElement> target, String subdirUnderLoad) {
        if (subdirUnderLoad == null || subdirUnderLoad.isBlank()) {
            return 0;
        }
        String directoryUnderData = LoadJson.LOAD_FOLDER + "/" + subdirUnderLoad;
        return scanConfiguredRoots((dataRoot) -> scanDataRootForDir(dataRoot, directoryUnderData, target));
    }

    /**
     * Merges JSON under {@code data/<ns>/<directoryUnderData>/…} (e.g. {@code iska_lib/liquids}).
     */
    public static int mergeIntoDataDir(Map<Identifier, JsonElement> target, String directoryUnderData) {
        if (directoryUnderData == null || directoryUnderData.isBlank()) {
            return 0;
        }
        return scanConfiguredRoots((dataRoot) -> scanDataRootForDir(dataRoot, directoryUnderData, target));
    }

    @FunctionalInterface
    private interface DataRootScanner {
        int scan(Path dataRoot);
    }

    private static int scanConfiguredRoots(DataRootScanner scanner) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        List<? extends String> paths = IskaLibConfig.resolveBootstrapDatapackPaths();
        int count = 0;
        for (String raw : paths) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path root = gameDir.resolve(raw.trim()).normalize();
            if (!Files.exists(root)) {
                continue;
            }
            count += scanRoot(root, scanner);
        }
        return count;
    }

    private static int scanRoot(Path root, DataRootScanner scanner) {
        if (Files.isRegularFile(root) && isPackArchive(root)) {
            return scanZipPack(root, scanner);
        }
        if (!Files.isDirectory(root)) {
            return 0;
        }
        if (isPackRoot(root)) {
            int count = 0;
            try (Stream<Path> children = Files.list(root)) {
                for (Path child : children.toList()) {
                    if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("pack.mcmeta"))) {
                        count += scanner.scan(child.resolve("data"));
                    } else if (Files.isRegularFile(child) && isPackArchive(child)) {
                        count += scanZipPack(child, scanner);
                    } else if (Files.isDirectory(child) && Files.isDirectory(child.resolve("data"))) {
                        // Folder pack without pack.mcmeta (some loaders allow this)
                        count += scanner.scan(child.resolve("data"));
                    }
                }
            } catch (IOException ex) {
                LOGGER.debug("Could not list pack root {}: {}", root, ex.getMessage());
            }
            return count;
        }
        return scanner.scan(root);
    }

    private static boolean isPackRoot(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (Files.isRegularFile(child) && isPackArchive(child)) {
                    return true;
                }
                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("pack.mcmeta"))) {
                    return true;
                }
            }
        } catch (IOException ex) {
            LOGGER.debug("Could not inspect {}: {}", root, ex.getMessage());
        }
        return false;
    }

    private static boolean isPackArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static int scanZipPack(Path zipPath, DataRootScanner scanner) {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath)) {
            Path dataRoot = fs.getPath("data");
            if (Files.isDirectory(dataRoot)) {
                return scanner.scan(dataRoot);
            }
            // Some archives nest a single pack folder at the zip root
            try (Stream<Path> roots = Files.list(fs.getPath("/"))) {
                for (Path child : roots.toList()) {
                    Path nestedData = child.resolve("data");
                    if (Files.isDirectory(nestedData)) {
                        return scanner.scan(nestedData);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read datapack zip {}: {}", zipPath, ex.getMessage());
        }
        return 0;
    }

    private static int scanDataRootForTypes(Path dataRoot, Set<String> acceptedTypes, Map<Identifier, JsonElement> target) {
        if (!Files.isDirectory(dataRoot)) {
            return 0;
        }
        String loadSegment = "/" + LoadJson.LOAD_FOLDER + "/";
        int count = 0;
        try (Stream<Path> walk = Files.walk(dataRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String normalized = file.toString().replace('\\', '/');
                Path rel = dataRoot.relativize(file);
                if (rel.getNameCount() < 3) {
                    continue;
                }
                boolean underLoad = normalized.contains(loadSegment);
                boolean flatUnderLoad = isFlatUnderLoad(rel);
                if (!underLoad && !flatUnderLoad) {
                    continue;
                }
                if (!tryPutJson(file, rel, target, parsed -> typeMatches(parsed, acceptedTypes))) {
                    continue;
                }
                count++;
            }
        } catch (IOException ex) {
            LOGGER.debug("Could not walk {}: {}", dataRoot, ex.getMessage());
        }
        return count;
    }

    private static int scanDataRootForDir(Path dataRoot, String directoryUnderData, Map<Identifier, JsonElement> target) {
        if (!Files.isDirectory(dataRoot)) {
            return 0;
        }
        String dirSegment = "/" + directoryUnderData + "/";
        int count = 0;
        try (Stream<Path> walk = Files.walk(dataRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String normalized = file.toString().replace('\\', '/');
                Path rel = dataRoot.relativize(file);
                if (rel.getNameCount() < 3 || !normalized.contains(dirSegment)) {
                    continue;
                }
                if (!tryPutJson(file, rel, target, parsed -> parsed != null)) {
                    continue;
                }
                count++;
            }
        } catch (IOException ex) {
            LOGGER.debug("Could not walk {}: {}", dataRoot, ex.getMessage());
        }
        return count;
    }

    @FunctionalInterface
    private interface JsonFilter {
        boolean accept(JsonElement parsed);
    }

    private static boolean tryPutJson(Path file, Path rel, Map<Identifier, JsonElement> target, JsonFilter filter) {
        String namespace = rel.getName(0).toString();
        String pathPart = rel.toString().replace('\\', '/');
        Identifier id;
        try {
            id = Identifier.fromNamespaceAndPath(namespace, pathPart);
        } catch (Exception ex) {
            LOGGER.debug("Invalid resource id for {}: {}", file, ex.getMessage());
            return false;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
            if (!filter.accept(parsed)) {
                return false;
            }
            target.put(id, parsed);
            return true;
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Failed to read external JSON {}: {}", file, ex.getMessage());
            return false;
        }
    }

    private static boolean isFlatUnderLoad(Path relFromDataRoot) {
        return relFromDataRoot.getNameCount() == 3
                && LoadJson.LOAD_FOLDER.equals(relFromDataRoot.getName(1).toString());
    }

    private static boolean typeMatches(JsonElement element, Set<String> acceptedTypes) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) {
            return false;
        }
        return acceptedTypes.contains(obj.get("type").getAsString());
    }
}
