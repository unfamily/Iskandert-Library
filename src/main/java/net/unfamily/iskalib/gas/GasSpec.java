package net.unfamily.iskalib.gas;

import net.unfamily.iskalib.liquid.DimensionTickTransform;
import net.unfamily.iskalib.liquid.LiquidSoundSet;

import java.util.List;

/**
 * Immutable registration request for one gas type in a consumer mod namespace.
 */
public record GasSpec(
        String modId,
        String name,
        int tintArgb,
        String descriptionId,
        int lightLevel,
        int tickInterval,
        GasTypeProperties typeProperties,
        GasFlowingProperties flowProperties,
        LiquidSoundSet sounds,
        List<DimensionTickTransform> dimensionTicks
) {
    public static final int DEFAULT_TICK_INTERVAL = 10;

    public GasSpec {
        dimensionTicks = dimensionTicks == null ? List.of() : List.copyOf(dimensionTicks);
    }

    public GasSpec(String modId, String name, int tintArgb) {
        this(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, DEFAULT_TICK_INTERVAL);
    }

    public GasSpec(String modId, String name, int tintArgb, String descriptionId) {
        this(modId, name, tintArgb, descriptionId, 0, DEFAULT_TICK_INTERVAL);
    }

    public GasSpec(String modId, String name, int tintArgb, String descriptionId, int lightLevel, int tickInterval) {
        this(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                GasTypeProperties.STANDARD_GAS, GasFlowingProperties.STANDARD, LiquidSoundSet.DEFAULT, List.of());
    }

    public static String defaultDescriptionId(String modId, String name) {
        return "fluid." + modId + ".gas_fluid_" + name;
    }

    public GasSpec withTypeProperties(GasTypeProperties typeProperties) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds, dimensionTicks);
    }

    public GasSpec withFlowProperties(GasFlowingProperties flowProperties) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds, dimensionTicks);
    }

    public GasSpec withSounds(LiquidSoundSet sounds) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds, dimensionTicks);
    }

    public GasSpec withDimensionTicks(List<DimensionTickTransform> dimensionTicks) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds, dimensionTicks);
    }

    /** Rebinds this spec to another mod id (Library JSON gases always register under {@code iska_lib}). */
    public GasSpec withModId(String newModId) {
        String desc = descriptionId;
        if (defaultDescriptionId(modId, name).equals(descriptionId)) {
            desc = defaultDescriptionId(newModId, name);
        }
        return new GasSpec(newModId, name, tintArgb, desc, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds, dimensionTicks);
    }

    public String fluidSourceId() {
        return "gas_fluid_" + name;
    }

    public String fluidFlowingId() {
        return fluidSourceId() + "_flowing";
    }

    public String blockId() {
        return "gas_" + name;
    }

    public String bucketId() {
        return blockId() + "_bucket";
    }
}
