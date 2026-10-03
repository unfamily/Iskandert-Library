package net.unfamily.iskalib.crafting;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory server datapack that exposes split recipe JSON children (and disabled parents)
 * so {@code RecipeManager} loads progressive holder ids without a mixin.
 */
public final class RecipeBundleVirtualPack extends AbstractPackResources {
    public static final String PACK_ID = "iska_lib/recipe_bundle_bridge";

    private static final PackMetadataSection PACK_META = new PackMetadataSection(
            Component.literal("IskaLib Recipe Bundle Bridge"),
            SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));

    private static volatile RecipeBundleVirtualPack active;

    private final Map<ResourceLocation, byte[]> resources = new ConcurrentHashMap<>();
    private final Set<String> namespaces = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public RecipeBundleVirtualPack(PackLocationInfo location) {
        super(location);
        active = this;
    }

    public static void invalidate() {
        RecipeBundleVirtualPack pack = active;
        if (pack != null) {
            pack.dirty.set(true);
        }
    }

    private void ensureBuilt() {
        if (!dirty.get() && !resources.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (!dirty.get() && !resources.isEmpty()) {
                return;
            }
            Map<ResourceLocation, byte[]> built = RecipeBundleBridgeBuilder.buildOverlayBytes();
            resources.clear();
            namespaces.clear();
            resources.putAll(built);
            for (ResourceLocation id : built.keySet()) {
                namespaces.add(id.getNamespace());
            }
            dirty.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        return "pack".equals(deserializer.getMetadataSectionName()) ? (T) PACK_META : null;
    }

    @Override
    public void close() {}

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length == 1 && PACK_META_NAME.equals(paths[0])) {
            String json = "{\"pack\":{\"description\":\"IskaLib Recipe Bundle Bridge\",\"pack_format\":"
                    + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
                    + "}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
        }
        return null;
    }

    private static final String PACK_META_NAME = "pack.mcmeta";

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        ensureBuilt();
        byte[] data = resources.get(location);
        if (data == null) {
            return null;
        }
        return () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            return;
        }
        ensureBuilt();
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getNamespace().equals(namespace)) {
                continue;
            }
            String p = id.getPath();
            if (p.equals(path) || p.startsWith(prefix)) {
                byte[] data = entry.getValue();
                output.accept(id, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.SERVER_DATA) {
            return Collections.emptySet();
        }
        ensureBuilt();
        return Set.copyOf(namespaces.isEmpty() ? Set.of() : new HashSet<>(namespaces));
    }

    public static PackMetadataSection metadata() {
        return PACK_META;
    }
}
