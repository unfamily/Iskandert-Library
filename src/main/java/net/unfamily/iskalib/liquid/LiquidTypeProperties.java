package net.unfamily.iskalib.liquid;

import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * Mirrors NeoForge {@link FluidType.Properties} for library liquid registration.
 */
public record LiquidTypeProperties(
        double motionScale,
        boolean canPushEntity,
        boolean canSwim,
        boolean canDrown,
        float fallDistanceModifier,
        boolean canExtinguish,
        boolean canConvertToSource,
        boolean supportsBoating,
        boolean canHydrate,
        @Nullable PathType pathType,
        @Nullable PathType adjacentPathType,
        Rarity rarity,
        int density,
        int temperature,
        int viscosity
) {
    /** NeoForge {@link FluidType.Properties} defaults. */
    public static final LiquidTypeProperties DEFAULT = new LiquidTypeProperties(
            0.014D,
            true,
            true,
            true,
            0.5F,
            false,
            false,
            false,
            false,
            PathType.WATER,
            PathType.WATER_BORDER,
            Rarity.COMMON,
            1000,
            300,
            1000);

    /** Colossal molten metal convention: hot, viscous, not swimmable. */
    public static final LiquidTypeProperties MOLTEN = new LiquidTypeProperties(
            DEFAULT.motionScale,
            true,
            false,
            false,
            DEFAULT.fallDistanceModifier,
            DEFAULT.canExtinguish,
            false,
            DEFAULT.supportsBoating,
            DEFAULT.canHydrate,
            DEFAULT.pathType,
            DEFAULT.adjacentPathType,
            DEFAULT.rarity,
            1000,
            1300,
            6000);

    /** Colossal gelid breezium: cold water-like movement. */
    public static final LiquidTypeProperties COLD_WATER_LIKE = new LiquidTypeProperties(
            DEFAULT.motionScale,
            true,
            true,
            true,
            DEFAULT.fallDistanceModifier,
            DEFAULT.canExtinguish,
            false,
            DEFAULT.supportsBoating,
            DEFAULT.canHydrate,
            DEFAULT.pathType,
            DEFAULT.adjacentPathType,
            DEFAULT.rarity,
            1000,
            260,
            1000);

    /**
     * When {@code loggable} is set on fluid JSON: water-like FluidType behaviour
     * (hydrate, boating, extinguish). Vanilla {@code SimpleWaterloggedBlock} only stores
     * {@link net.minecraft.world.level.material.Fluids#WATER}, so custom fluids cannot
     * occupy the WATERLOGGED blockstate; this is the supported “loggable” contract.
     */
    public LiquidTypeProperties withLoggableDefaults() {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, true,
                canConvertToSource, true, true, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withCanConvertToSource(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                value, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withSupportsBoating(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, value, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withCanHydrate(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, value, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withTemperature(int temperature) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withViscosity(int viscosity) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withDensity(int density) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withCanSwim(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, value, canDrown, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withCanDrown(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, value, fallDistanceModifier, canExtinguish,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    public LiquidTypeProperties withCanExtinguish(boolean value) {
        return new LiquidTypeProperties(
                motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, value,
                canConvertToSource, supportsBoating, canHydrate, pathType, adjacentPathType, rarity, density, temperature, viscosity);
    }

    FluidType.Properties build(String descriptionId, int lightLevel, LiquidSoundSet sounds) {
        FluidType.Properties props = FluidType.Properties.create()
                .descriptionId(descriptionId)
                .lightLevel(lightLevel)
                .motionScale(motionScale)
                .canPushEntity(canPushEntity)
                .canSwim(canSwim)
                .canDrown(canDrown)
                .fallDistanceModifier(fallDistanceModifier)
                .canExtinguish(canExtinguish)
                .canConvertToSource(canConvertToSource)
                .supportsBoating(supportsBoating)
                .canHydrate(canHydrate)
                .density(density)
                .temperature(temperature)
                .viscosity(viscosity)
                .rarity(rarity);
        if (pathType != null) {
            props.pathType(pathType);
        }
        if (adjacentPathType != null) {
            props.adjacentPathType(adjacentPathType);
        }
        for (var entry : sounds.sounds().entrySet()) {
            props.sound(entry.getKey(), entry.getValue());
        }
        return props;
    }
}
