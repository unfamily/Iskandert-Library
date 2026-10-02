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
        int durability) {

    /** Omitted JSON / default Java: each AOE {@code destroyBlock} consumes durability normally. */
    public static final int DURABILITY_VANILLA = Integer.MIN_VALUE;

    /** No durability loss for AOE extra blocks. */
    public static final int DURABILITY_INFINITE = -1;

    public ToolBehaviorDefinition {
        if (range < 0) {
            range = 0;
        }
        harvestTags = harvestTags == null ? List.of() : List.copyOf(harvestTags);
    }

    public ToolBehaviorDefinition(
            Identifier itemId,
            ToolBehaviorType behavior,
            int range,
            List<TagKey<Block>> harvestTags) {
        this(itemId, behavior, range, harvestTags, DURABILITY_VANILLA);
    }

    public boolean isVanillaDurability() {
        return durability == DURABILITY_VANILLA;
    }

    public boolean isInfiniteDurability() {
        return durability == DURABILITY_INFINITE;
    }
}
