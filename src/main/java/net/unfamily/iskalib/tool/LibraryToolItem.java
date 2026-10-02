package net.unfamily.iskalib.tool;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * Library-owned digger registered from tool JSON ({@code iska_lib:<id>}) at startup only.
 */
public final class LibraryToolItem extends Item {
    private final ToolBehaviorType behavior;

    private LibraryToolItem(ToolBehaviorType behavior, Item.Properties properties) {
        super(properties);
        this.behavior = behavior;
    }

    public static Item.Properties baseProperties(ToolBehaviorDefinition def) {
        LibraryToolStats.Resolved stats = def.stats().resolve(def.behavior());
        TagKey<Item> repair = stats.repairTag() != null ? stats.repairTag() : ItemTags.IRON_TOOL_MATERIALS;
        ToolMaterial material = new ToolMaterial(
                stats.incorrectForDrops(),
                stats.materialDurability(),
                stats.miningSpeed(),
                stats.attackDamageBonus(),
                stats.enchantability(),
                repair);

        TagKey<Block> mineable = switch (def.behavior()) {
            case LUMBERJACK -> BlockTags.MINEABLE_WITH_AXE;
            case SCYTHE -> BlockTags.MINEABLE_WITH_HOE;
            case EXCAVATOR, PAXEL -> BlockTags.MINEABLE_WITH_PICKAXE;
        };

        Item.Properties props = material.applyToolProperties(
                new Item.Properties(),
                mineable,
                stats.attackDamageBaseline(),
                stats.attackSpeed(),
                stats.disableBlockingSeconds());

        if (def.behavior() == ToolBehaviorType.PAXEL || def.behavior() == ToolBehaviorType.EXCAVATOR) {
            props.component(DataComponents.TOOL, createMultiMineTool(def.behavior(), material));
        }

        if (stats.repairItem() != null) {
            Item repairItem = BuiltInRegistries.ITEM.getValue(stats.repairItem());
            if (repairItem != null && repairItem != Items.AIR) {
                props.repairable(repairItem);
            }
        }

        if (stats.fireResistant()) {
            props.fireResistant();
        }
        if (stats.rarity() != null) {
            props.rarity(stats.rarity());
        }

        int durability = def.durability();
        if (durability == ToolBehaviorDefinition.DURABILITY_INFINITE) {
            props.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        } else if (!def.isVanillaDurability() && durability >= 0) {
            props.durability(durability);
        }
        return props;
    }

    public static LibraryToolItem create(ToolBehaviorDefinition def, Item.Properties properties) {
        return new LibraryToolItem(def.behavior(), properties);
    }

    private static Tool createMultiMineTool(ToolBehaviorType behavior, ToolMaterial material) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        float speed = material.speed();
        if (behavior == ToolBehaviorType.EXCAVATOR) {
            return new Tool(
                    List.of(
                            Tool.Rule.deniesDrops(blocks.getOrThrow(material.incorrectBlocksForDrops())),
                            Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), speed),
                            Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), speed)),
                    1.0F,
                    1,
                    true);
        }
        return new Tool(
                List.of(
                        Tool.Rule.deniesDrops(blocks.getOrThrow(material.incorrectBlocksForDrops())),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), speed),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), speed),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), speed)),
                1.0F,
                1,
                true);
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return switch (behavior) {
            case LUMBERJACK -> ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
            case SCYTHE -> ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability);
            case EXCAVATOR -> ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
            case PAXEL -> ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)
                    || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
        };
    }
}
