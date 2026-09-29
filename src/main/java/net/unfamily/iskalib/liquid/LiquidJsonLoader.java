package net.unfamily.iskalib.liquid;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import net.unfamily.iskalib.IskaLib;
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
import java.util.stream.Stream;

/**
 * Loads liquid definitions from {@code data/<namespace>/iska_lib/liquids/<id>.json}.
 * <p>
 * JSON cannot supply a custom {@link LiquidBlockFactory}. At Library init, jar resources under
 * {@code iska_lib} are scanned and registered via {@link IskaLibLiquids#registerLibraryLiquid}
 * (fluid + block + bucket). Datapack reload updates {@link LiquidBehaviorRegistry} overlays
 * (infinity / loggable / dimension ticks) for matching fluid ids; new fluid ids still require a restart.
 */
public final class LiquidJsonLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiquidJsonLoader.class);
    private static final Gson GSON = new Gson();
    /** Relative to {@code data/<namespace>/}. */
    public static final String LIQUIDS_SUBDIR = "iska_lib/liquids";
    private static final String JAR_DIR = "data/" + IskaLib.MOD_ID + "/" + LIQUIDS_SUBDIR;

    private LiquidJsonLoader() {}

    public static void bootstrapFromJar() {
        bootstrapAndRegister(false);
    }

    /**
     * Scans Library jar liquids JSON, applies overlays, and registers brand-new {@code iska_lib} fluids.
     *
     * @param registerFluids when true, calls {@link IskaLibLiquids#registerLibraryLiquid} for each
     *                       parsed {@code iska_lib} spec not already registered (mod-init only)
     */
    public static void bootstrapAndRegister(boolean registerFluids) {
        Map<ResourceLocation, JsonObject> parsed = scanJarLiquids();
        if (registerFluids) {
            registerParsedLibraryFluids(parsed);
        }
        applyParsed(parsed, true);
    }

    private static Map<ResourceLocation, JsonObject> scanJarLiquids() {
        Map<ResourceLocation, JsonObject> parsed = new LinkedHashMap<>();
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
                LOGGER.warn("Failed to bootstrap liquid JSON from jar: {}", e.getMessage());
            }
        });
        return parsed;
    }

    private static void registerParsedLibraryFluids(Map<ResourceLocation, JsonObject> parsed) {
        int registered = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : parsed.entrySet()) {
            try {
                ParsedLiquid parsedLiquid = parse(entry.getKey(), entry.getValue());
                if (!IskaLib.MOD_ID.equals(parsedLiquid.spec().modId())) {
                    continue;
                }
                if (IskaLibLiquids.isRegistered(parsedLiquid.fluidId())) {
                    continue;
                }
                IskaLibLiquids.registerLibraryLiquid(parsedLiquid.spec());
                registered++;
                LOGGER.info("Registered library liquid from JSON: {}", parsedLiquid.fluidId());
            } catch (RuntimeException error) {
                LOGGER.warn("Skipping library liquid registration for {}: {}", entry.getKey(), error.getMessage());
            }
        }
        if (registered > 0) {
            LOGGER.info("Library JSON liquids registered at bootstrap: {}", registered);
        }
    }

    public static void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            bootstrapFromJar();
            return;
        }
        Map<ResourceLocation, JsonObject> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, List<Resource>> stacks = resourceManager.listResourceStacks(
                LIQUIDS_SUBDIR,
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, List<Resource>> entry : stacks.entrySet()) {
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
                LOGGER.warn("Failed to read liquid JSON {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        applyParsed(parsed, false);
    }

    private static void applyParsed(Map<ResourceLocation, JsonObject> parsed, boolean fromJar) {
        Map<ResourceLocation, LiquidBehaviorRegistry.Overlay> overlays = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : parsed.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try {
                ParsedLiquid parsedLiquid = parse(resourceId, entry.getValue());
                overlays.put(parsedLiquid.fluidId(), parsedLiquid.overlay());
                if (fromJar) {
                    LOGGER.debug("Loaded built-in liquid JSON {} -> {}", resourceId, parsedLiquid.fluidId());
                }
            } catch (RuntimeException error) {
                LOGGER.warn("Invalid liquid JSON {}: {}", resourceId, error.getMessage());
            }
        }
        // Preserve Java-registered overlays that datapacks did not redefine.
        Map<ResourceLocation, LiquidBehaviorRegistry.Overlay> merged = new LinkedHashMap<>(LiquidBehaviorRegistry.all());
        merged.putAll(overlays);
        LiquidBehaviorRegistry.putAll(merged);
        LOGGER.info("Liquid JSON overlays loaded: {} (source={})", overlays.size(), fromJar ? "jar" : "datapack");
    }

    public static Optional<LiquidSpec> parseSpec(ResourceLocation resourceId, JsonObject json) {
        try {
            return Optional.of(parse(resourceId, json).spec());
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to parse LiquidSpec from {}: {}", resourceId, error.getMessage());
            return Optional.empty();
        }
    }

    private static ParsedLiquid parse(ResourceLocation resourceId, JsonObject json) {
        String namespace = resourceId.getNamespace();
        String fileName = fileNameWithoutExtension(resourceId);
        String name = stringOr(json, "name", fileName);
        int tint = parseTint(json.get("tint"));
        String descriptionId = stringOr(json, "description_id", LiquidSpec.defaultDescriptionId(namespace, name));
        int light = json.has("light") ? json.get("light").getAsInt() : 0;
        boolean bucket = !json.has("bucket") || json.get("bucket").getAsBoolean();
        Boolean infinityOverride = optionalBool(json, "infinity");
        if (infinityOverride == null) {
            infinityOverride = optionalBool(json, "can_convert_to_source");
        }
        Boolean loggableOverride = optionalBool(json, "loggable");
        boolean infinity = infinityOverride != null && infinityOverride;
        boolean loggable = loggableOverride != null && loggableOverride;

        ResourceLocation still = parseTexture(json, "still", "still_texture",
                ResourceLocation.fromNamespaceAndPath(namespace, LiquidSpec.DEFAULT_STILL_PATH));
        ResourceLocation flowing = parseTexture(json, "flow", "flowing_texture",
                ResourceLocation.fromNamespaceAndPath(namespace, LiquidSpec.DEFAULT_FLOWING_PATH));
        ResourceLocation overlay = parseOptionalTexture(json, "overlay", "overlay_texture");

        LiquidTypeProperties type = LiquidTypeProperties.DEFAULT.withCanConvertToSource(infinity);
        if (json.has("type") && json.get("type").isJsonObject()) {
            type = applyTypeSubset(type, json.getAsJsonObject("type"));
        } else if (json.has("type_properties") && json.get("type_properties").isJsonObject()) {
            type = applyTypeSubset(type, json.getAsJsonObject("type_properties"));
        }
        if (loggable) {
            type = type.withLoggableDefaults().withCanConvertToSource(infinity || type.canConvertToSource());
        }

        FlowingFluidProperties flow = FlowingFluidProperties.DEFAULT;
        if (json.has("flow_properties") && json.get("flow_properties").isJsonObject()) {
            flow = applyFlowSubset(flow, json.getAsJsonObject("flow_properties"));
        } else if (json.has("flow_props") && json.get("flow_props").isJsonObject()) {
            flow = applyFlowSubset(flow, json.getAsJsonObject("flow_props"));
        }

        List<DimensionTickTransform> ticks = parseDimensionTicks(json.get("dimension_ticks"));

        LiquidSpec spec = new LiquidSpec(
                namespace, name, tint, descriptionId, light, still, flowing, bucket,
                type, flow, LiquidBlockProperties.STANDARD.withLoggable(loggable),
                overlay == null ? LiquidClientProperties.NONE : LiquidClientProperties.withOverlay(overlay),
                LiquidSoundSet.DEFAULT,
                ticks);

        ResourceLocation fluidId = ResourceLocation.fromNamespaceAndPath(namespace, name);
        LiquidBehaviorRegistry.Overlay overlayBehavior =
                new LiquidBehaviorRegistry.Overlay(infinityOverride, loggableOverride, ticks);
        return new ParsedLiquid(fluidId, spec, overlayBehavior);
    }

    private static LiquidTypeProperties applyTypeSubset(LiquidTypeProperties base, JsonObject obj) {
        LiquidTypeProperties type = base;
        if (obj.has("can_convert_to_source")) {
            type = type.withCanConvertToSource(obj.get("can_convert_to_source").getAsBoolean());
        }
        if (obj.has("supports_boating")) {
            type = type.withSupportsBoating(obj.get("supports_boating").getAsBoolean());
        }
        if (obj.has("can_hydrate")) {
            type = type.withCanHydrate(obj.get("can_hydrate").getAsBoolean());
        }
        if (obj.has("can_swim")) {
            type = type.withCanSwim(obj.get("can_swim").getAsBoolean());
        }
        if (obj.has("can_drown")) {
            type = type.withCanDrown(obj.get("can_drown").getAsBoolean());
        }
        if (obj.has("can_extinguish")) {
            type = type.withCanExtinguish(obj.get("can_extinguish").getAsBoolean());
        }
        if (obj.has("temperature")) {
            type = type.withTemperature(obj.get("temperature").getAsInt());
        }
        if (obj.has("viscosity")) {
            type = type.withViscosity(obj.get("viscosity").getAsInt());
        }
        if (obj.has("density")) {
            type = type.withDensity(obj.get("density").getAsInt());
        }
        return type;
    }

    private static FlowingFluidProperties applyFlowSubset(FlowingFluidProperties base, JsonObject obj) {
        FlowingFluidProperties flow = base;
        if (obj.has("tick_rate")) {
            flow = flow.withTickRate(obj.get("tick_rate").getAsInt());
        }
        if (obj.has("slope_find_distance")) {
            flow = flow.withSlopeFindDistance(obj.get("slope_find_distance").getAsInt());
        }
        if (obj.has("level_decrease_per_block")) {
            flow = flow.withLevelDecreasePerBlock(obj.get("level_decrease_per_block").getAsInt());
        }
        if (obj.has("explosion_resistance")) {
            flow = flow.withExplosionResistance(obj.get("explosion_resistance").getAsFloat());
        }
        return flow;
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
            ResourceLocation dimension = ResourceLocation.parse(obj.get("dimension").getAsString());
            int interval = obj.has("interval")
                    ? obj.get("interval").getAsInt()
                    : (obj.has("interval_ticks") ? obj.get("interval_ticks").getAsInt() : 20);
            ResourceLocation transformTo = null;
            if (obj.has("transform_to")) {
                transformTo = ResourceLocation.parse(obj.get("transform_to").getAsString());
            }
            String effect = null;
            if (obj.has("effect")) {
                effect = obj.get("effect").getAsString();
            } else if (obj.has("effect_preset")) {
                effect = obj.get("effect_preset").getAsString();
            } else if (obj.has("effect_preset_id")) {
                effect = obj.get("effect_preset_id").getAsString();
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

    private static ResourceLocation parseTexture(JsonObject json, String shortKey, String longKey, ResourceLocation fallback) {
        if (json.has(shortKey)) {
            return ResourceLocation.parse(json.get(shortKey).getAsString());
        }
        if (json.has(longKey)) {
            return ResourceLocation.parse(json.get(longKey).getAsString());
        }
        return fallback;
    }

    private static ResourceLocation parseOptionalTexture(JsonObject json, String shortKey, String longKey) {
        if (json.has(shortKey)) {
            return ResourceLocation.parse(json.get(shortKey).getAsString());
        }
        if (json.has(longKey)) {
            return ResourceLocation.parse(json.get(longKey).getAsString());
        }
        return null;
    }

    private static boolean boolOr(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    @Nullable
    private static Boolean optionalBool(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsBoolean() : null;
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static String fileNameWithoutExtension(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return file.substring(0, file.length() - 5);
        }
        return file;
    }

    private static void readPathJson(Map<ResourceLocation, JsonObject> out, Path base, Path file, String namespace) {
        try {
            String relative = base.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".json")) {
                return;
            }
            String idPath = LIQUIDS_SUBDIR + "/" + relative;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, idPath);
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null && element.isJsonObject()) {
                    out.put(id, element.getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Failed to read liquid JSON {}: {}", file, ex.getMessage());
        }
    }

    private record ParsedLiquid(ResourceLocation fluidId, LiquidSpec spec, LiquidBehaviorRegistry.Overlay overlay) {}
}
