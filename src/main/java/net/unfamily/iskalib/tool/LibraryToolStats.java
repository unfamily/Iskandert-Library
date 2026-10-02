package net.unfamily.iskalib.tool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Pack-dev item stats for Library-created tools ({@code iska_lib:<id>}).
 * Harvest level is the {@code incorrect_for_drops} block tag (no named tier).
 */
public record LibraryToolStats(
        @Nullable Float miningSpeed,
        @Nullable Float attackDamage,
        @Nullable Float attackSpeed,
        @Nullable Integer enchantability,
        @Nullable TagKey<Block> incorrectForDrops,
        @Nullable TagKey<Item> repairTag,
        @Nullable ResourceLocation repairItem,
        @Nullable Float disableBlockingSeconds,
        boolean fireResistant,
        @Nullable Rarity rarity) {

    private static final float DEFAULT_SPEED = 6.0F;
    private static final int DEFAULT_ENCHANT = 14;
    private static final int DEFAULT_MATERIAL_DURABILITY = 250;

    public static final LibraryToolStats DEFAULT = new LibraryToolStats(
            null, null, null, null, null, null, null, null, false, null);

    public record Resolved(
            TagKey<Block> incorrectForDrops,
            float miningSpeed,
            float attackDamageBonus,
            int enchantability,
            Ingredient repairIngredient,
            float attackDamageBaseline,
            float attackSpeed,
            float disableBlockingSeconds,
            boolean fireResistant,
            @Nullable Rarity rarity,
            int materialDurability) {}

    public Resolved resolve(ToolBehaviorType behavior) {
        float speed = miningSpeed != null ? miningSpeed : DEFAULT_SPEED;
        float baseline = attackDamage != null ? attackDamage : defaultAttackBaseline(behavior);
        float atkSpeed = attackSpeed != null ? attackSpeed : defaultAttackSpeed(behavior);
        int enchant = enchantability != null ? enchantability : DEFAULT_ENCHANT;
        TagKey<Block> incorrect = incorrectForDrops != null
                ? incorrectForDrops
                : BlockTags.INCORRECT_FOR_IRON_TOOL;
        float blockSecs = disableBlockingSeconds != null ? disableBlockingSeconds : 0.0F;

        Ingredient repair;
        if (repairTag != null) {
            repair = Ingredient.of(repairTag);
        } else if (repairItem != null) {
            Item item = BuiltInRegistries.ITEM.get(repairItem);
            repair = item != null && item != Items.AIR ? Ingredient.of(item) : Ingredient.of(Items.IRON_INGOT);
        } else {
            repair = Ingredient.of(Items.IRON_INGOT);
        }

        return new Resolved(
                incorrect,
                speed,
                0.0F,
                enchant,
                repair,
                baseline,
                atkSpeed,
                blockSecs,
                fireResistant,
                rarity,
                DEFAULT_MATERIAL_DURABILITY);
    }

    private static float defaultAttackBaseline(ToolBehaviorType behavior) {
        return switch (behavior) {
            case LUMBERJACK -> 6.0F;
            case SCYTHE -> 0.0F;
            case EXCAVATOR, PAXEL -> 1.0F;
        };
    }

    private static float defaultAttackSpeed(ToolBehaviorType behavior) {
        return switch (behavior) {
            case LUMBERJACK -> -3.1F;
            case SCYTHE -> -1.0F;
            case EXCAVATOR, PAXEL -> -2.8F;
        };
    }
}
