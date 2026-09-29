package net.unfamily.iskalib.liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Liquid block that applies {@link DimensionTickTransform} presets while ticking in matching dimensions.
 * Also consults {@link LiquidBehaviorRegistry} overlays for datapack-driven transforms.
 */
public class DimensionTickLiquidBlock extends LiquidBlock {
    private final ResourceLocation sourceFluidId;
    private final List<DimensionTickTransform> registeredTransforms;

    public DimensionTickLiquidBlock(
            FlowingFluid fluid,
            BlockBehaviour.Properties properties,
            ResourceLocation sourceFluidId,
            List<DimensionTickTransform> registeredTransforms
    ) {
        super(fluid, properties);
        this.sourceFluidId = sourceFluidId;
        this.registeredTransforms = registeredTransforms == null ? List.of() : List.copyOf(registeredTransforms);
    }

    public ResourceLocation sourceFluidId() {
        return sourceFluidId;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        scheduleIfNeeded(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DimensionTickTransform match = findMatchingTransform(level);
        if (match != null) {
            applyTransform(level, pos, match);
            if (level.getBlockState(pos).getBlock() == this) {
                level.scheduleTick(pos, this, match.intervalTicks());
            }
            return;
        }
        super.tick(state, level, pos, random);
    }

    private void scheduleIfNeeded(Level level, BlockPos pos) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DimensionTickTransform match = findMatchingTransform(serverLevel);
        if (match != null) {
            level.scheduleTick(pos, this, match.intervalTicks());
        }
    }

    @Nullable
    private DimensionTickTransform findMatchingTransform(ServerLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        List<DimensionTickTransform> transforms = LiquidBehaviorRegistry.dimensionTicks(sourceFluidId, registeredTransforms);
        for (DimensionTickTransform transform : transforms) {
            if (transform.dimension().equals(dimensionId)) {
                return transform;
            }
        }
        return null;
    }

    private void applyTransform(ServerLevel level, BlockPos pos, DimensionTickTransform transform) {
        String preset = transform.resolvedPreset();
        switch (preset) {
            case "remove", "evaporate" -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            case "convert_to" -> convertTo(level, pos, transform.transformTo());
            case "damage" -> damageEntities(level, pos);
            default -> {
                if (transform.transformTo() != null) {
                    convertTo(level, pos, transform.transformTo());
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void convertTo(ServerLevel level, BlockPos pos, @Nullable ResourceLocation targetId) {
        if (targetId == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(targetId).orElse(null);
        if (block != null) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(targetId).orElse(null);
        if (fluid != null && !fluid.defaultFluidState().isEmpty()) {
            level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void damageEntities(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos);
        DamageSources sources = level.damageSources();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            entity.hurt(sources.onFire(), 1.0F);
        }
    }
}
