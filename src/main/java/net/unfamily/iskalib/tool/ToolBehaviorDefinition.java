package net.unfamily.iskalib.tool;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;

public record ToolBehaviorDefinition(
        Identifier itemId,
        ToolBehaviorType behavior,
        int range,
        List<TagKey<Block>> harvestTags,
        int durability,
        LibraryToolStats stats) {

    /** Omitted JSON: use default max durability when omitted; AOE damages normally per block. */
    public static final int DURABILITY_VANILLA = Integer.MIN_VALUE;

    /** Unbreakable item; AOE extras do not consume durability. */
    public static final int DURABILITY_INFINITE = -1;

    public ToolBehaviorDefinition {
        if (range < 0) {
            range = 0;
        }
        harvestTags = harvestTags == null ? List.of() : List.copyOf(harvestTags);
        stats = stats == null ? LibraryToolStats.DEFAULT : stats;
    }

    public ToolBehaviorDefinition(
            Identifier itemId,
            ToolBehaviorType behavior,
            int range,
            List<TagKey<Block>> harvestTags,
            int durability) {
        this(itemId, behavior, range, harvestTags, durability, LibraryToolStats.DEFAULT);
    }

    public ToolBehaviorDefinition(
            Identifier itemId,
            ToolBehaviorType behavior,
            int range,
            List<TagKey<Block>> harvestTags) {
        this(itemId, behavior, range, harvestTags, DURABILITY_VANILLA, LibraryToolStats.DEFAULT);
    }

    public boolean isVanillaDurability() {
        return durability == DURABILITY_VANILLA;
    }

    public boolean isInfiniteDurability() {
        return durability == DURABILITY_INFINITE;
    }
}
