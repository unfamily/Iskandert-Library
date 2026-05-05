package net.unfamily.iskalib.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses item strings (with optional data components) into ItemStacks.
 *
 * <p>Supports both simple format {@code minecraft:diamond_sword} and component format
 * {@code minecraft:diamond_sword[minecraft:damage=500,...]} (same shape as the {@code /give} item argument).
 */
public final class ItemConverter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ItemConverter() {}

    public static ItemStack parseItemString(String itemString, int count) {
        if (itemString == null || itemString.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.warn("Server not available for item parsing: {}", itemString);
                return fallbackParsing(itemString, count);
            }

            try {
                HolderLookup.Provider registryAccess = server.registryAccess();
                ItemParser itemParser = new ItemParser(registryAccess);
                StringReader reader = new StringReader(itemString);

                var itemInput = itemParser.parse(reader);
                return itemInput.createItemStack(count);
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Error parsing item '{}': {}. Attempting fallback.", itemString, e.getMessage());
                return fallbackParsing(itemString, count);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error during item parsing '{}': {}", itemString, e.getMessage());
            return fallbackParsing(itemString, count);
        }
    }

    public static ItemStack parseItemString(String itemString) {
        return parseItemString(itemString, 1);
    }

    public static boolean isValidItemString(String itemString) {
        if (itemString == null || itemString.trim().isEmpty()) {
            return false;
        }

        try {
            ItemStack result = parseItemString(itemString, 1);
            return !result.isEmpty() && result.getItem() != Items.STONE;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getItemDisplayName(String itemString) {
        ItemStack stack = parseItemString(itemString, 1);
        return !stack.isEmpty() ? stack.getHoverName().getString() : itemString;
    }

    /**
     * KubeJS-like item string without quotes or count prefix.
     *
     * <p>Example: {@code minecraft:diamond_sword[damage=5,custom_data={...}]}
     * where component keys in the {@code minecraft} namespace are reduced to just the path (e.g. {@code damage}).
     */
    public static String formatAsKubeJsItemString(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                return formatAsKubeJsItemString(stack, server.registryAccess());
            } catch (RuntimeException e) {
                LOGGER.warn("KubeJS item string encoding failed, falling back to /give shape: {}", e.getMessage());
            }
        }
        return formatAsItemArgumentLegacy(stack);
    }

    /**
     * Same as {@link #formatAsKubeJsItemString(ItemStack)} with an explicit registry context.
     */
    public static String formatAsKubeJsItemString(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return "";
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return itemId.toString();
        }

        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        List<Map.Entry<DataComponentType<?>, Optional<?>>> entries = new ArrayList<>(patch.entrySet());
        entries.sort(Comparator.comparing(e -> {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(e.getKey());
            return id != null ? id.toString() : "";
        }));

        StringBuilder bracket = new StringBuilder();
        boolean first = true;
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : entries) {
            DataComponentType<?> type = entry.getKey();
            Optional<?> opt = entry.getValue();
            Identifier compId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (compId == null) {
                continue;
            }

            if (!first) {
                bracket.append(',');
            }
            first = false;

            String compKey = "minecraft".equals(compId.getNamespace()) ? compId.getPath() : compId.toString();
            if (opt.isEmpty()) {
                bracket.append('!').append(compKey);
            } else {
                @SuppressWarnings("unchecked")
                DataComponentType<Object> typed = (DataComponentType<Object>) type;
                Object value = opt.get();
                DataResult<Tag> encoded = typed.codecOrThrow().encodeStart(ops, value);
                Tag tag = encoded.getOrThrow();
                bracket.append(compKey).append('=').append(tag);
            }
        }

        return itemId + "[" + bracket + "]";
    }

    /**
     * JSON compatible representation of {@link #formatAsKubeJsItemString(ItemStack)}.
     *
     * <p>It wraps the string in double quotes and performs exactly two replacements:
     * {@code \} → {@code \\} and {@code "} → {@code \"}.
     */
    public static String formatAsKubeJsItemStringJson(ItemStack stack) {
        String raw = formatAsKubeJsItemString(stack);
        if (raw.isEmpty()) {
            return "\"\"";
        }
        raw = raw.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + raw + "\"";
    }

    /**
     * Item id plus data-component bracket in the same SNBT shape as {@link ItemParser} / {@code /give}.
     * Encodes each component with its registry codec to NBT so nested quotes (e.g. {@code item_name}) use SNBT escaping.
     */
    public static String formatAsItemArgument(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                return formatAsItemArgument(stack, server.registryAccess());
            } catch (RuntimeException e) {
                LOGGER.warn("SNBT item argument encoding failed, using legacy patch string: {}", e.getMessage());
            }
        }
        return formatAsItemArgumentLegacy(stack);
    }

    /**
     * Same as {@link #formatAsItemArgument(ItemStack)} with an explicit registry context (e.g. player on server).
     */
    public static String formatAsItemArgument(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return "";
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return itemId.toString();
        }
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        List<Map.Entry<DataComponentType<?>, Optional<?>>> entries = new ArrayList<>(patch.entrySet());
        entries.sort(Comparator.comparing(e -> {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(e.getKey());
            return id != null ? id.toString() : "";
        }));

        StringBuilder bracket = new StringBuilder();
        boolean first = true;
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : entries) {
            DataComponentType<?> type = entry.getKey();
            Optional<?> opt = entry.getValue();
            Identifier compId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (compId == null) {
                continue;
            }
            if (!first) {
                bracket.append(',');
            }
            first = false;
            if (opt.isEmpty()) {
                bracket.append('!').append(compId);
            } else {
                @SuppressWarnings("unchecked")
                DataComponentType<Object> typed = (DataComponentType<Object>) type;
                Object value = opt.get();
                DataResult<Tag> encoded = typed.codecOrThrow().encodeStart(ops, value);
                Tag tag = encoded.getOrThrow();
                bracket.append(compId).append('=').append(tag);
            }
        }
        return itemId + "[" + bracket + "]";
    }

    private static String formatAsItemArgumentLegacy(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return id.toString();
        }
        String patchStr = patch.toString();
        if (patchStr.startsWith("{") && patchStr.endsWith("}")) {
            patchStr = patchStr.substring(1, patchStr.length() - 1);
        }
        patchStr = patchStr.replace("=>", "=");
        return id + "[" + patchStr + "]";
    }

    private static ItemStack fallbackParsing(String itemString, int count) {
        try {
            String itemId = extractItemId(itemString);

            Identifier itemResource = Identifier.tryParse(itemId);
            var item = BuiltInRegistries.ITEM.getOptional(itemResource).orElse(null);

            if (item != Items.AIR) {
                return new ItemStack(item, count);
            }
        } catch (Exception e) {
            LOGGER.warn("Fallback parsing failed for '{}': {}", itemString, e.getMessage());
        }

        LOGGER.warn("Unable to parse item '{}', using stone as fallback", itemString);
        return new ItemStack(Items.STONE, count);
    }

    private static String extractItemId(String itemString) {
        int bracketIndex = itemString.indexOf('[');
        return bracketIndex != -1 ? itemString.substring(0, bracketIndex) : itemString;
    }
}

