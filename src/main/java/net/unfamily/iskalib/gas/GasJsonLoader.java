package net.unfamily.iskalib.gas;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.load.LoadFilesystemBootstrap;
import net.unfamily.iskalib.load.LoadJson;
import net.unfamily.iskalib.liquid.DimensionTickTransform;
import net.unfamily.iskalib.liquid.LiquidBehaviorRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads gas definitions from {@code data/<namespace>/iska_lib/gases/<id>.json}.
 * Root {@code type} must be {@link #TYPE_GAS}; path id from {@code id} (default: file name).
 * <p>
 * Gases are a special fluid: they rise and dissipate instead of spreading horizontally like liquids.
 * JSON never accepts {@code flow_properties}. Supports {@code dimension_ticks} overlays via
 * {@link net.unfamily.iskalib.liquid.LiquidBehaviorRegistry}. At Library init, jar resources under
 * {@code iska_lib} are registered via {@link IskaLibGases#registerLibraryGas}. Datapack reload
 * updates dimension-tick overlays (new gas ids still require a restart for registry-time registration).
 */
public final class GasJsonLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GasJsonLoader.class);
    private static final Gson GSON = new Gson();

    public static final String TYPE_GAS = "iska_lib:gas";
    public static final String GASES_SUBDIR = "iska_lib/gases";
    private static final String JAR_DIR = "data/" + IskaLib.MOD_ID + "/" + GASES_SUBDIR;

    private GasJsonLoader() {}

    public static void bootstrapFromJar() {
        bootstrapAndRegister(false);
    }

    public static void bootstrapAndRegister(boolean registerGases) {
        Map<Identifier, JsonObject> parsed = scanJarGases();
        int external = mergeExternalGases(parsed);
        if (external > 0) {
            LOGGER.info("Gas JSON bootstrap: merged {} file(s) from kubejs/datapacks on disk", external);
        }
        if (registerGases) {
            registerParsedLibraryGases(parsed);
        }
        applyParsed(parsed, true);
    }

    private static int mergeExternalGases(Map<Identifier, JsonObject> parsed) {
        Map<Identifier, JsonElement> external = new LinkedHashMap<>();
        int count = LoadFilesystemBootstrap.mergeIntoDataDir(external, GASES_SUBDIR);
        count += LoadFilesystemBootstrap.mergeIntoForType(external, TYPE_GAS);
        int objects = 0;
        for (Map.Entry<Identifier, JsonElement> entry : external.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                parsed.put(entry.getKey(), entry.getValue().getAsJsonObject());
                objects++;
            }
        }
        return objects > 0 ? count : 0;
    }

    private static Map<Identifier, JsonObject> scanJarGases() {
        Map<Identifier, JsonObject> parsed = new LinkedHashMap<>();
        ModList.get().getModContainerById(IskaLib.MOD_ID).ifPresent(container -> {
            var owning = container.getModInfo().getOwningFile();
            if (owning == null) {
                return;
            }
            Path root = owning.getFile().getFilePath();
            try {
                if (Files.isDirectory(root)) {
                    Path buildDir = root.getParent() != null && root.getParent().getParent() != null
                            ? root.getParent().getParent().getParent()
                            : null;
                    Path resources = buildDir != null ? buildDir.resolve("resources").resolve("main") : null;
                    Path base = resources != null && Files.exists(resources.resolve(JAR_DIR))
                            ? resources.resolve(JAR_DIR)
                            : root.resolve(JAR_DIR);
                    if (Files.exists(base)) {
                        try (Stream<Path> walk = Files.walk(base)) {
                            walk.filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".json"))
                                    .sorted()
                                    .forEach(file -> readPathJson(parsed, base, file, IskaLib.MOD_ID));
                        }
                    }
                } else {
                    try (var fs = FileSystems.newFileSystem(root)) {
                        Path base = fs.getPath(JAR_DIR);
                        if (Files.exists(base)) {
                            try (Stream<Path> walk = Files.walk(base)) {
                                walk.filter(Files::isRegularFile)
                                        .filter(p -> p.toString().endsWith(".json"))
                                        .sorted()
                                        .forEach(file -> readPathJson(parsed, base, file, IskaLib.MOD_ID));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to bootstrap gas JSON from jar: {}", e.getMessage());
            }
        });
        return parsed;
    }

    private static void registerParsedLibraryGases(Map<Identifier, JsonObject> parsed) {
        int registered = 0;
        for (Map.Entry<Identifier, JsonObject> entry : parsed.entrySet()) {
            try {
                ParsedGas parsedGas = parse(entry.getKey(), entry.getValue());
                GasSpec spec = asLibraryOwned(parsedGas.spec());
                Identifier fluidId = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, spec.fluidSourceId());
                if (IskaLibGases.isRegistered(fluidId)) {
                    continue;
                }
                IskaLibGases.registerLibraryGas(spec);
                registered++;
                LOGGER.info("Registered library gas from JSON: {} (source {})", fluidId, entry.getKey());
            } catch (RuntimeException error) {
                LOGGER.warn("Skipping library gas registration for {}: {}", entry.getKey(), error.getMessage());
            }
        }
        if (registered > 0) {
            LOGGER.info("Library JSON gases registered at bootstrap: {}", registered);
        }
    }

    private static GasSpec asLibraryOwned(GasSpec spec) {
        return IskaLib.MOD_ID.equals(spec.modId()) ? spec : spec.withModId(IskaLib.MOD_ID);
    }

    public static void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            bootstrapFromJar();
            return;
        }
        Map<Identifier, JsonObject> parsed = new LinkedHashMap<>();
        Map<Identifier, List<Resource>> stacks = resourceManager.listResourceStacks(
                GASES_SUBDIR,
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            Resource top = stack.get(stack.size() - 1);
            try (var reader = new BufferedReader(new InputStreamReader(top.open(), StandardCharsets.UTF_8))) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null && element.isJsonObject()) {
                    parsed.put(entry.getKey(), element.getAsJsonObject());
                }
            } catch (IOException | JsonParseException ex) {
                LOGGER.warn("Failed to read gas JSON {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        for (Map.Entry<Identifier, JsonElement> entry :
                LoadJson.collectMergedJsonForTypes(resourceManager, Set.of(TYPE_GAS)).entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                parsed.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
        applyParsed(parsed, false);
    }

    private static void applyParsed(Map<Identifier, JsonObject> parsed, boolean fromJar) {
        Map<Identifier, LiquidBehaviorRegistry.Overlay> overlays = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonObject> entry : parsed.entrySet()) {
            Identifier resourceId = entry.getKey();
            try {
                ParsedGas parsedGas = parse(resourceId, entry.getValue());
                GasSpec librarySpec = asLibraryOwned(parsedGas.spec());
                Identifier fluidId = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, librarySpec.fluidSourceId());
                overlays.put(fluidId, parsedGas.overlay());
                if (fromJar) {
                    LOGGER.debug("Loaded gas JSON {} -> {}", resourceId, fluidId);
                }
            } catch (RuntimeException error) {
                LOGGER.warn("Invalid gas JSON {}: {}", resourceId, error.getMessage());
            }
        }
        Map<Identifier, LiquidBehaviorRegistry.Overlay> merged = new LinkedHashMap<>(LiquidBehaviorRegistry.all());
        merged.putAll(overlays);
        LiquidBehaviorRegistry.putAll(merged);
        LOGGER.info("Gas JSON overlays loaded: {} (source={})", overlays.size(), fromJar ? "jar" : "datapack");
    }

    public static Optional<GasSpec> parseSpec(Identifier resourceId, JsonObject json) {
        try {
            return Optional.of(parse(resourceId, json).spec());
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to parse GasSpec from {}: {}", resourceId, error.getMessage());
            return Optional.empty();
        }
    }

    private static ParsedGas parse(Identifier resourceId, JsonObject json) {
        assertGasType(resourceId, json);
        String namespace = resourceId.getNamespace();
        String fileName = fileNameWithoutExtension(resourceId);
        String id = resolveId(json, fileName);
        int tint = parseTint(json.get("tint"));
        String descriptionId = stringOr(json, "description_id", GasSpec.defaultDescriptionId(namespace, id));
        int light = json.has("light") ? json.get("light").getAsInt() : 0;
        int tickInterval = json.has("tick_interval")
                ? json.get("tick_interval").getAsInt()
                : GasSpec.DEFAULT_TICK_INTERVAL;

        GasTypeProperties type = GasTypeProperties.STANDARD_GAS;
        JsonObject typeProps = typePropertiesObject(json);
        if (typeProps != null) {
            type = applyTypeSubset(type, typeProps);
        }

        if (json.has("flow_properties")) {
            LOGGER.warn("Gas JSON {} ignores flow_properties (gases rise; they do not spread like liquids)", resourceId);
        }

        List<DimensionTickTransform> ticks = parseDimensionTicks(json.get("dimension_ticks"));

        GasSpec spec = new GasSpec(namespace, id, tint, descriptionId, light, tickInterval)
                .withTypeProperties(type)
                .withDimensionTicks(ticks);
        Identifier fluidId = Identifier.fromNamespaceAndPath(namespace, spec.fluidSourceId());
        LiquidBehaviorRegistry.Overlay overlay = new LiquidBehaviorRegistry.Overlay(null, null, ticks);
        return new ParsedGas(fluidId, spec, overlay);
    }

    private static void assertGasType(Identifier resourceId, JsonObject json) {
        if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
            throw new IllegalArgumentException("missing type " + TYPE_GAS + " in " + resourceId);
        }
        String type = json.get("type").getAsString();
        if (!TYPE_GAS.equals(type)) {
            throw new IllegalArgumentException("expected type " + TYPE_GAS + ", got " + type);
        }
    }

    private static String resolveId(JsonObject json, String fileName) {
        if (json.has("id") && json.get("id").isJsonPrimitive()) {
            String id = json.get("id").getAsString().trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        return fileName;
    }

    @Nullable
    private static JsonObject typePropertiesObject(JsonObject json) {
        if (json.has("type_properties") && json.get("type_properties").isJsonObject()) {
            return json.getAsJsonObject("type_properties");
        }
        return null;
    }

    private static GasTypeProperties applyTypeSubset(GasTypeProperties base, JsonObject obj) {
        double motionScale = obj.has("motion_scale") ? obj.get("motion_scale").getAsDouble() : base.motionScale();
        boolean canPushEntity = obj.has("can_push_entity") ? obj.get("can_push_entity").getAsBoolean() : base.canPushEntity();
        boolean canSwim = obj.has("can_swim") ? obj.get("can_swim").getAsBoolean() : base.canSwim();
        boolean canDrown = obj.has("can_drown") ? obj.get("can_drown").getAsBoolean() : base.canDrown();
        float fallDistanceModifier = obj.has("fall_distance_modifier")
                ? obj.get("fall_distance_modifier").getAsFloat()
                : base.fallDistanceModifier();
        boolean canExtinguish = obj.has("can_extinguish") ? obj.get("can_extinguish").getAsBoolean() : base.canExtinguish();
        boolean canConvertToSource = obj.has("can_convert_to_source")
                ? obj.get("can_convert_to_source").getAsBoolean()
                : base.canConvertToSource();
        boolean supportsBoating = obj.has("supports_boating") ? obj.get("supports_boating").getAsBoolean() : base.supportsBoating();
        boolean canHydrate = obj.has("can_hydrate") ? obj.get("can_hydrate").getAsBoolean() : base.canHydrate();
        int density = obj.has("density") ? obj.get("density").getAsInt() : base.density();
        int temperature = obj.has("temperature") ? obj.get("temperature").getAsInt() : base.temperature();
        int viscosity = obj.has("viscosity") ? obj.get("viscosity").getAsInt() : base.viscosity();
        return new GasTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier,
                canExtinguish, canConvertToSource, supportsBoating, canHydrate,
                base.pathType(), base.adjacentPathType(), base.rarity(),
                density, temperature, viscosity);
    }

    private static List<DimensionTickTransform> parseDimensionTicks(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<DimensionTickTransform> out = new ArrayList<>();
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject obj = entry.getAsJsonObject();
            if (!obj.has("dimension")) {
                continue;
            }
            Identifier dimension = Identifier.parse(obj.get("dimension").getAsString());
            int interval = obj.has("interval") ? obj.get("interval").getAsInt() : 20;
            Identifier transformTo = null;
            if (obj.has("transform_to")) {
                transformTo = Identifier.parse(obj.get("transform_to").getAsString());
            }
            String effect = null;
            if (obj.has("effect")) {
                effect = obj.get("effect").getAsString();
            }
            out.add(new DimensionTickTransform(dimension, interval, transformTo, effect));
        }
        return List.copyOf(out);
    }

    private static int parseTint(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0xFFFFFFFF;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        String text = element.getAsString().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() == 6) {
            return (int) Long.parseLong(text, 16) | 0xFF000000;
        }
        if (text.length() == 8) {
            return (int) Long.parseLong(text, 16);
        }
        throw new IllegalArgumentException("Invalid tint: " + element);
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static String fileNameWithoutExtension(Identifier resourceId) {
        String path = resourceId.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return file.substring(0, file.length() - 5);
        }
        return file;
    }

    private static void readPathJson(Map<Identifier, JsonObject> out, Path base, Path file, String namespace) {
        try {
            String relative = base.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".json")) {
                return;
            }
            String idPath = GASES_SUBDIR + "/" + relative;
            Identifier id = Identifier.fromNamespaceAndPath(namespace, idPath);
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null && element.isJsonObject()) {
                    out.put(id, element.getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Failed to read gas JSON {}: {}", file, ex.getMessage());
        }
    }

    private record ParsedGas(Identifier fluidId, GasSpec spec, LiquidBehaviorRegistry.Overlay overlay) {}
}
