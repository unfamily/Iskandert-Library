package net.unfamily.iskalib.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.IskaLibConfig;

import java.util.List;

/**
 * Applies sticky slowdown for configured fluids and posts {@link StickyFluidEvents.InCrudeOil}
 * when the entity stands in a configured crude oil fluid.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class StickyFluidEffects {
    private static final Vec3 STICKY_MOTION_MULTIPLIER = new Vec3(0.25D, 0.05D, 0.25D);

    private StickyFluidEffects() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        FluidState state = entity.level().getFluidState(entity.blockPosition());
        if (state.isEmpty()) {
            return;
        }

        if (matchesAny(state, IskaLibConfig.resolveStickyFluids())) {
            entity.makeStuckInBlock(Blocks.AIR.defaultBlockState(), STICKY_MOTION_MULTIPLIER);
        }

        if (!entity.level().isClientSide() && matchesAny(state, IskaLibConfig.resolveCrudeOils())) {
            NeoForge.EVENT_BUS.post(new StickyFluidEvents.InCrudeOil(entity, state));
        }
    }

    /** True when {@code state} matches any configured sticky fluid entry. */
    public static boolean isSticky(FluidState state) {
        return !state.isEmpty() && matchesAny(state, IskaLibConfig.resolveStickyFluids());
    }

    /** True when {@code state} matches any configured crude oil entry. */
    public static boolean isCrudeOil(FluidState state) {
        return !state.isEmpty() && matchesAny(state, IskaLibConfig.resolveCrudeOils());
    }

    private static boolean matchesAny(FluidState state, List<? extends String> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        for (String entry : entries) {
            if (entry != null && matches(state, entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(FluidState state, String entry) {
        if (entry.isEmpty()) {
            return false;
        }
        if (entry.startsWith("#")) {
            String tagName = entry.substring(1).trim();
            if (tagName.isEmpty()) {
                return false;
            }
            try {
                return state.is(FluidTags.create(ResourceLocation.parse(tagName)));
            } catch (RuntimeException ex) {
                return false;
            }
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(state.getType());
        return fluidId != null && fluidId.toString().equals(entry);
    }
}
