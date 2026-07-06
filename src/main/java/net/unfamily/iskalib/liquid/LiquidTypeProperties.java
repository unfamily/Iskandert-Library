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
