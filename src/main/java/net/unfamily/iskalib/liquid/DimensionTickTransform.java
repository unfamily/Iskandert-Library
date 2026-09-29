package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Per-dimension scheduled transform applied while a liquid block ticks in that dimension.
 *
 * @param dimension     dimension id (e.g. {@code minecraft:the_nether})
 * @param intervalTicks tick interval between applications (minimum 1)
 * @param transformTo   optional block or fluid id used by convert presets
 * @param effectPreset  optional preset id: {@code evaporate}, {@code remove}, {@code convert_to}, {@code damage}
 */
public record DimensionTickTransform(
        Identifier dimension,
        int intervalTicks,
        @Nullable Identifier transformTo,
        @Nullable String effectPreset
) {
    public DimensionTickTransform {
        intervalTicks = Math.max(1, intervalTicks);
    }

    public static DimensionTickTransform of(Identifier dimension, int intervalTicks) {
        return new DimensionTickTransform(dimension, intervalTicks, null, null);
    }

    public static DimensionTickTransform evaporate(Identifier dimension, int intervalTicks) {
        return new DimensionTickTransform(dimension, intervalTicks, null, "evaporate");
    }

    public static DimensionTickTransform convertTo(Identifier dimension, int intervalTicks, Identifier transformTo) {
        return new DimensionTickTransform(dimension, intervalTicks, transformTo, "convert_to");
    }

    public static DimensionTickTransform damage(Identifier dimension, int intervalTicks) {
        return new DimensionTickTransform(dimension, intervalTicks, null, "damage");
    }

    public String resolvedPreset() {
        if (effectPreset != null && !effectPreset.isBlank()) {
            return effectPreset.trim().toLowerCase();
        }
        if (transformTo != null) {
            return "convert_to";
        }
        return "evaporate";
    }
}
