package net.unfamily.iskalib.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * Library-owned digger registered from tool JSON ({@code iska_lib:<id>}) at startup only.
 */
public final class LibraryToolItem extends DiggerItem {
    private final ToolBehaviorType behavior;

    private LibraryToolItem(ToolBehaviorType behavior, TagKey<Block> mineable, Item.Properties properties) {
        super(Tiers.IRON, mineable, properties);
        this.behavior = behavior;
    }

    public static Item.Properties baseProperties(ToolBehaviorDefinition def) {
        Item.Properties props = new Item.Properties()
                .attributes(DiggerItem.createAttributes(Tiers.IRON, 1.0F, -2.8F));
        int durability = def.durability();
        if (durability == ToolBehaviorDefinition.DURABILITY_INFINITE) {
            props.component(DataComponents.UNBREAKABLE, new Unbreakable(true));
        } else if (!def.isVanillaDurability() && durability >= 0) {
            props.durability(durability);
        }
        if (def.behavior() == ToolBehaviorType.PAXEL || def.behavior() == ToolBehaviorType.EXCAVATOR) {
            props.component(DataComponents.TOOL, createMultiMineTool(def.behavior()));
        }
        return props;
    }

    public static LibraryToolItem create(ToolBehaviorDefinition def, Item.Properties properties) {
        TagKey<Block> tag = switch (def.behavior()) {
            case LUMBERJACK -> BlockTags.MINEABLE_WITH_AXE;
            case SCYTHE -> BlockTags.MINEABLE_WITH_HOE;
            case EXCAVATOR, PAXEL -> BlockTags.MINEABLE_WITH_PICKAXE;
        };
        return new LibraryToolItem(def.behavior(), tag, properties);
    }

    private static Tool createMultiMineTool(ToolBehaviorType behavior) {
        float speed = Tiers.IRON.getSpeed();
        if (behavior == ToolBehaviorType.EXCAVATOR) {
            return new Tool(
                    List.of(
                            Tool.Rule.deniesDrops(Tiers.IRON.getIncorrectBlocksForDrops()),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed),
                            Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed)),
                    1.0F,
                    1);
        }
        return new Tool(
                List.of(
                        Tool.Rule.deniesDrops(Tiers.IRON.getIncorrectBlocksForDrops()),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, speed),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed)),
                1.0F,
                1);
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
}
