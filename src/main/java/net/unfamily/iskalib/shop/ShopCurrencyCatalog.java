package net.unfamily.iskalib.shop;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Built-in currency catalog loaded from {@code data/iska_utils/load/iska_utils_shop/}
 * (shipped in the Library jar under the Utils data namespace). Accepts
 * {@code iska_utils:shop_currency}, {@code iska_lib:shop_currency}, and legacy {@code iska_utils:shop_valute}.
 */
public final class ShopCurrencyCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopCurrencyCatalog.class);
    private static final Gson GSON = new Gson();
    private static final String LOAD_SUBDIR = "load/iska_utils_shop";
    /** Same datapath as Utils shop load; contributed by this mod's jar. */
    private static final String JAR_DIR = "data/iska_utils/" + LOAD_SUBDIR;

    public record Entry(String id, String translationKey, String symbol) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final ShopCurrencyHooks.Listener CATALOG_LISTENER = new ShopCurrencyHooks.Listener() {
        @Override
        public List<String> listCurrencyIds() {
            return List.copyOf(ENTRIES.keySet());
        }

        @Override
        public Optional<ShopCurrencyHooks.CurrencyInfo> getCurrencyInfo(String currencyId) {
            Entry entry = ENTRIES.get(currencyId);
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(new ShopCurrencyHooks.CurrencyInfo(entry.translationKey(), entry.symbol()));
        }
    };

    private ShopCurrencyCatalog() {}

    public static ShopCurrencyHooks.Listener listener() {
        return CATALOG_LISTENER;
    }

    public static Map<String, Entry> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    public static void bootstrapFromJar() {
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
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
                                    .forEach(file -> readPathJson(merged, base, file));
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
                                        .forEach(file -> readPathJson(merged, base, file));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to bootstrap currency catalog from jar: {}", e.getMessage());
            }
        });
        applyMerged(merged);
    }

    public static void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            bootstrapFromJar();
            return;
        }
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
        Map<ResourceLocation, List<Resource>> stacks = resourceManager.listResourceStacks(
                LOAD_SUBDIR,
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, List<Resource>> entry : stacks.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            Resource top = stack.get(stack.size() - 1);
            try (var reader = new BufferedReader(new InputStreamReader(top.open(), StandardCharsets.UTF_8))) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null) {
                    merged.put(entry.getKey(), parsed);
                }
            } catch (IOException | JsonParseException ex) {
                LOGGER.warn("Failed to read currency JSON {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        // Also accept flat load/*.json that declare a shop currency type.
        Map<ResourceLocation, List<Resource>> flat = resourceManager.listResourceStacks(
                "load",
                id -> {
                    String path = id.getPath();
                    return path.endsWith(".json") && path.indexOf('/') < 0;
                });
        for (Map.Entry<ResourceLocation, List<Resource>> entry : flat.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            Resource top = stack.get(stack.size() - 1);
            try (var reader = new BufferedReader(new InputStreamReader(top.open(), StandardCharsets.UTF_8))) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null && parsed.isJsonObject() && isCurrencyType(parsed.getAsJsonObject())) {
                    merged.put(entry.getKey(), parsed);
                }
            } catch (IOException | JsonParseException ignored) {
                // ignore non-currency flat files
            }
        }
        applyMerged(merged);
        if (ENTRIES.isEmpty()) {
            bootstrapFromJar();
        }
    }

    private static void applyMerged(Map<ResourceLocation, JsonElement> merged) {
        ENTRIES.clear();
        List<Map.Entry<ResourceLocation, JsonElement>> ordered = new ArrayList<>(merged.entrySet());
        ordered.sort((a, b) -> {
            boolean aLib = IskaLib.MOD_ID.equals(a.getKey().getNamespace());
            boolean bLib = IskaLib.MOD_ID.equals(b.getKey().getNamespace());
            if (aLib != bLib) {
                return aLib ? -1 : 1;
            }
            boolean aUtils = "iska_utils".equals(a.getKey().getNamespace());
            boolean bUtils = "iska_utils".equals(b.getKey().getNamespace());
            if (aUtils != bUtils) {
                return aUtils ? -1 : 1;
            }
            return a.getKey().toString().compareTo(b.getKey().toString());
        });
        for (Map.Entry<ResourceLocation, JsonElement> e : ordered) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            ingest(e.getValue().getAsJsonObject());
        }
        installDefaultListenerIfNeeded();
    }

    private static void ingest(JsonObject json) {
        if (!isCurrencyType(json)) {
            return;
        }
        boolean overwritable = json.has("overwritable") && json.get("overwritable").getAsBoolean();
        if (!json.has("currencies") || !json.get("currencies").isJsonArray()) {
            return;
        }
        JsonArray array = json.getAsJsonArray("currencies");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : null;
            if (id == null || id.isBlank()) {
                continue;
            }
            if (ENTRIES.containsKey(id) && !overwritable) {
                continue;
            }
            String name = obj.has("name") ? obj.get("name").getAsString() : id;
            String symbol = obj.has("char_symbol")
                    ? obj.get("char_symbol").getAsString()
                    : (obj.has("symbol") ? obj.get("symbol").getAsString() : "Ø");
            ENTRIES.put(id, new Entry(id, name, symbol));
        }
    }

    private static boolean isCurrencyType(JsonObject json) {
        if (!json.has("type")) {
            return false;
        }
        String type = json.get("type").getAsString().trim();
        if (!type.contains(":")) {
            type = "iska_utils:" + type;
        }
        return "iska_lib:shop_currency".equals(type)
                || "iska_utils:shop_currency".equals(type)
                || "iska_utils:shop_valute".equals(type)
                || "shop_currency".equals(type)
                || "shop_valute".equals(type);
    }

    private static void readPathJson(Map<ResourceLocation, JsonElement> out, Path base, Path file) {
        try {
            String relative = base.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".json")) {
                return;
            }
            String defId = relative.substring(0, relative.length() - 5);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("iska_utils", LOAD_SUBDIR + "/" + defId);
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null) {
                    out.put(id, parsed);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
        }
    }

    /**
     * Installs catalog as the default ShopCurrencyHooks listener when nothing else has registered yet,
     * or when the current listener is this catalog (reload).
     */
    public static void installDefaultListenerIfNeeded() {
        ShopCurrencyHooks.Listener current = ShopCurrencyHooks.getListener();
        if (current == CATALOG_LISTENER || current.listCurrencyIds().isEmpty()) {
            ShopCurrencyHooks.setListener(CATALOG_LISTENER);
        }
    }

    /** Force catalog listener (used at Library init before Utils may override). */
    public static void installAsDefaultListener() {
        ShopCurrencyHooks.setListener(CATALOG_LISTENER);
    }
}
