package net.unfamily.iskalib.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Parses item strings (with optional data components) into ItemStacks.
 *
 * <p>Supports both simple format {@code minecraft:diamond_sword} and component format
 * {@code minecraft:diamond_sword[damage=500,enchantments={sharpness:3}]}
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

