package net.unfamily.iskalib.liquid;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/**
 * Liquid block registration properties (separate from fluid-type light level when needed).
 */
public record LiquidBlockProperties(
        MapColor mapColor,
        float strength,
        PushReaction pushReaction,
        int blockLightLevel,
        @Nullable LiquidBlockFactory blockFactory
) {
    public static final LiquidBlockProperties STANDARD = new LiquidBlockProperties(
            MapColor.COLOR_GRAY, 100.0F, PushReaction.DESTROY, -1, null);

    public BlockBehaviour.Properties toBlockProperties(int fluidTypeLightLevel) {
        int light = blockLightLevel >= 0 ? blockLightLevel : fluidTypeLightLevel;
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .replaceable()
                .strength(strength)
                .pushReaction(pushReaction)
                .noLootTable()
                .liquid()
                .lightLevel(state -> light);
    }

    public LiquidBlock createBlock(FlowingFluid flowing, BlockBehaviour.Properties props) {
        if (blockFactory != null) {
            return blockFactory.create(flowing, props);
        }
        return new LiquidBlock(flowing, props);
    }

    public LiquidBlockProperties withBlockFactory(LiquidBlockFactory factory) {
        return new LiquidBlockProperties(mapColor, strength, pushReaction, blockLightLevel, factory);
    }

    public LiquidBlockProperties withBlockLightLevel(int light) {
        return new LiquidBlockProperties(mapColor, strength, pushReaction, light, blockFactory);
    }
}
