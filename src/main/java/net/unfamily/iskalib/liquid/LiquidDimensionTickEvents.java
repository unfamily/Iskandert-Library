package net.unfamily.iskalib.liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.unfamily.iskalib.gas.GasLiquidBlock;
import net.unfamily.iskalib.IskaLib;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies {@link LiquidBehaviorRegistry} dimension-tick overlays to any fluid block,
 * including vanilla / third-party liquids that are not {@link DimensionTickLiquidBlock}.
 * {@link DimensionTickLiquidBlock} handles its own scheduled ticks; this path covers the rest.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class LiquidDimensionTickEvents {
    private record Tracked(ResourceLocation fluidId, long nextGameTime) {}

    private static final Map<ServerLevel, Map<BlockPos, Tracked>> TRACKED = new ConcurrentHashMap<>();
    private static final int MAX_APPLY_PER_LEVEL_TICK = 64;

    private LiquidDimensionTickEvents() {}

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        maybeTrack(level, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        maybeTrack(level, event.getPos(), event.getState());
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        maybeTrack(level, event.getPos(), event.getNewState());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Tracked> map = TRACKED.get(level);
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ResourceLocation dimensionId = level.dimension().location();
        int applied = 0;
        Iterator<Map.Entry<BlockPos, Tracked>> it = map.entrySet().iterator();
        while (it.hasNext() && applied < MAX_APPLY_PER_LEVEL_TICK) {
            Map.Entry<BlockPos, Tracked> entry = it.next();
            BlockPos pos = entry.getKey();
            Tracked tracked = entry.getValue();
            if (tracked.nextGameTime() > now) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DimensionTickLiquidBlock
                    || state.getBlock() instanceof GasLiquidBlock) {
                it.remove();
                continue;
            }
            ResourceLocation fluidId = resolveSourceFluidId(state);
            if (fluidId == null || !fluidId.equals(tracked.fluidId())) {
                it.remove();
                continue;
            }
            DimensionTickTransform match = findMatch(fluidId, dimensionId);
            if (match == null) {
                it.remove();
                continue;
            }
            applyTransform(level, pos, match);
            applied++;
            if (level.getBlockState(pos).getFluidState().isEmpty()) {
                it.remove();
            } else {
                entry.setValue(new Tracked(fluidId, now + Math.max(1, match.intervalTicks())));
            }
        }
        if (map.isEmpty()) {
            TRACKED.remove(level, map);
        }
    }

    private static void maybeTrack(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DimensionTickLiquidBlock
                || state.getBlock() instanceof GasLiquidBlock) {
            return;
        }
        ResourceLocation fluidId = resolveSourceFluidId(state);
        if (fluidId == null) {
            return;
        }
        DimensionTickTransform match = findMatch(fluidId, level.dimension().location());
        if (match == null) {
            return;
        }
        TRACKED
                .computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .put(pos.immutable(), new Tracked(fluidId, level.getGameTime() + Math.max(1, match.intervalTicks())));
    }

    @Nullable
    private static ResourceLocation resolveSourceFluidId(BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return null;
        }
        Fluid fluid = fluidState.getType();
        if (fluid instanceof FlowingFluid flowing) {
            fluid = flowing.getSource();
        }
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    @Nullable
    private static DimensionTickTransform findMatch(ResourceLocation fluidId, ResourceLocation dimensionId) {
        List<DimensionTickTransform> transforms = LiquidBehaviorRegistry.dimensionTicks(fluidId, List.of());
        for (DimensionTickTransform transform : transforms) {
            if (transform.dimension().equals(dimensionId)) {
                return transform;
            }
        }
        return null;
    }

    private static void applyTransform(ServerLevel level, BlockPos pos, DimensionTickTransform transform) {
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
