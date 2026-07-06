package net.unfamily.iskalib.gas;

import net.unfamily.iskalib.liquid.LiquidSoundSet;

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
        LiquidSoundSet sounds
) {
    public static final int DEFAULT_TICK_INTERVAL = 10;

    public GasSpec(String modId, String name, int tintArgb) {
        this(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, DEFAULT_TICK_INTERVAL);
    }

    public GasSpec(String modId, String name, int tintArgb, String descriptionId) {
        this(modId, name, tintArgb, descriptionId, 0, DEFAULT_TICK_INTERVAL);
    }

    public GasSpec(String modId, String name, int tintArgb, String descriptionId, int lightLevel, int tickInterval) {
        this(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                GasTypeProperties.STANDARD_GAS, GasFlowingProperties.STANDARD, LiquidSoundSet.DEFAULT);
    }

    public static String defaultDescriptionId(String modId, String name) {
        return "fluid." + modId + ".gas_fluid_" + name;
    }

    public GasSpec withTypeProperties(GasTypeProperties typeProperties) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds);
    }

    public GasSpec withFlowProperties(GasFlowingProperties flowProperties) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds);
    }

    public GasSpec withSounds(LiquidSoundSet sounds) {
        return new GasSpec(modId, name, tintArgb, descriptionId, lightLevel, tickInterval,
                typeProperties, flowProperties, sounds);
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
