package net.unfamily.iskalib.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * Library-owned digger registered from tool JSON ({@code iska_lib:<id>}) at startup only.
 */
public final class LibraryToolItem extends Item {
    private final ToolBehaviorType behavior;
    private final int enchantability;

    private LibraryToolItem(ToolBehaviorType behavior, int enchantability, Item.Properties properties) {
        super(properties);
        this.behavior = behavior;
        this.enchantability = enchantability;
    }

    public static Item.Properties baseProperties(ToolBehaviorDefinition def) {
        LibraryToolStats.Resolved stats = def.stats().resolve(def.behavior());
        Tier tier = new ResolvedTier(stats);

        TagKey<Block> mineable = switch (def.behavior()) {
            case LUMBERJACK -> BlockTags.MINEABLE_WITH_AXE;
            case SCYTHE -> BlockTags.MINEABLE_WITH_HOE;
            case EXCAVATOR, PAXEL -> BlockTags.MINEABLE_WITH_PICKAXE;
        };

        Item.Properties props = new Item.Properties()
                .attributes(createAttributes(stats))
                .component(DataComponents.TOOL, createToolComponent(def.behavior(), tier, mineable));

        if (stats.fireResistant()) {
            props.fireResistant();
        }
        if (stats.rarity() != null) {
            props.rarity(stats.rarity());
        }

        int durability = def.durability();
        if (durability == ToolBehaviorDefinition.DURABILITY_INFINITE) {
            props.component(DataComponents.UNBREAKABLE, new Unbreakable(true));
        } else if (!def.isVanillaDurability() && durability >= 0) {
            props.durability(durability);
        } else {
            props.durability(stats.materialDurability());
        }
        return props;
    }

    public static LibraryToolItem create(ToolBehaviorDefinition def, Item.Properties properties) {
        LibraryToolStats.Resolved stats = def.stats().resolve(def.behavior());
        return new LibraryToolItem(def.behavior(), stats.enchantability(), properties);
    }

    private static ItemAttributeModifiers createAttributes(LibraryToolStats.Resolved stats) {
        float damage = stats.attackDamageBaseline() + stats.attackDamageBonus();
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, stats.attackSpeed(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Tool createToolComponent(ToolBehaviorType behavior, Tier tier, TagKey<Block> primary) {
        float speed = tier.getSpeed();
        if (behavior == ToolBehaviorType.EXCAVATOR) {
            return new Tool(
                    List.of(
                            Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed)),
                    1.0F,
                    1);
        }
        if (behavior == ToolBehaviorType.PAXEL) {
            return new Tool(
                    List.of(
                            Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, speed),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed)),
                    1.0F,
                    1);
        }
        return new Tool(
                List.of(
                        Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()),
                        Tool.Rule.minesAndDrops(primary, speed)),
                1.0F,
                1);
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return switch (behavior) {
            case LUMBERJACK -> ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
            case SCYTHE -> ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability);
            case EXCAVATOR -> ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                    || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
            case PAXEL -> ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                    || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)
                    || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
        };
    }

    private record ResolvedTier(LibraryToolStats.Resolved stats) implements Tier {
        @Override
        public int getUses() {
            return stats.materialDurability();
        }

        @Override
        public float getSpeed() {
            return stats.miningSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return stats.attackDamageBonus();
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return stats.incorrectForDrops();
        }

        @Override
        public int getEnchantmentValue() {
            return stats.enchantability();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return stats.repairIngredient();
        }
    }
}
