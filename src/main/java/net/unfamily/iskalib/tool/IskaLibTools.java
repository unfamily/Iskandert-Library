package net.unfamily.iskalib.tool;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.liquid.IskaLibOwnedLiquidRegisters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers Library-owned tool items from JSON at mod construction (startup). New ids require a restart.
 */
public final class IskaLibTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(IskaLibTools.class);
    private static final Map<Identifier, DeferredItem<Item>> BY_ID = new LinkedHashMap<>();

    private IskaLibTools() {}

    public static boolean isRegistered(Identifier itemId) {
        return itemId != null && BY_ID.containsKey(itemId);
    }

    public static Collection<DeferredItem<Item>> allItems() {
        return BY_ID.values();
    }

    /**
     * Registers {@code iska_lib:<path>} if not already present. Call only during mod construction.
     */
    public static void registerLibraryTool(ToolBehaviorDefinition def) {
        if (def == null || def.itemId() == null) {
            return;
        }
        Identifier id = def.itemId();
        if (!IskaLib.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("Library JSON tools must use namespace " + IskaLib.MOD_ID + ", got " + id);
        }
        if (BY_ID.containsKey(id)) {
            return;
        }
        String path = id.getPath();
        DeferredItem<Item> holder = IskaLibOwnedLiquidRegisters.ITEMS.registerItem(
                path,
                props -> LibraryToolItem.create(def, props),
                props -> LibraryToolItem.baseProperties(def));
        BY_ID.put(id, holder);
        LibraryToolGeneratedAssets.writePlaceholderAssets(path);
        LOGGER.info("Registered Library tool item {}", id);
    }
}
