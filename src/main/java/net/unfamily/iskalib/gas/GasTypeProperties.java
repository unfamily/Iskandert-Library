package net.unfamily.iskalib.gas;

import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * Mirrors NeoForge {@link FluidType.Properties} for library gas registration.
 */
public record GasTypeProperties(
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
    /** Current IskaLibGases steam defaults. */
    public static final GasTypeProperties STANDARD_GAS = new GasTypeProperties(
            0.014D,
            false,
            false,
            false,
            0.5F,
            false,
            false,
            false,
            false,
            PathType.WATER,
            PathType.WATER_BORDER,
            Rarity.COMMON,
            -1000,
            300,
            100);

    FluidType.Properties build(String descriptionId, int lightLevel, net.unfamily.iskalib.liquid.LiquidSoundSet sounds) {
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
