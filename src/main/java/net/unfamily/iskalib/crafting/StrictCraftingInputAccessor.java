package net.unfamily.iskalib.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Attached to {@link net.minecraft.world.item.crafting.CraftingInput} instances created from
 * {@link net.minecraft.world.item.crafting.CraftingInput#ofPositioned} so strict shaped recipes can read the
 * original (un-trimmed) crafting grid.
 */
public interface StrictCraftingInputAccessor {
    void iska_lib$attachStrictContext(@Nullable List<ItemStack> fullStacks, int fullWidth, int fullHeight);

    boolean iska_lib$hasStrictContext();

    ItemStack iska_lib$getStrictSlot(int x, int y);
}
