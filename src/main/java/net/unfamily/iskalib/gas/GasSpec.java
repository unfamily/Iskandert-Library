package net.unfamily.iskalib.gas;

/**
 * Immutable registration request for one gas type in a consumer mod namespace.
 */
public record GasSpec(
        String modId,
        String name,
        int tintArgb,
        String descriptionId,
        int lightLevel,
        int tickInterval
) {
    public static final int DEFAULT_TICK_INTERVAL = 10;

    public GasSpec(String modId, String name, int tintArgb) {
        this(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, DEFAULT_TICK_INTERVAL);
    }

    public GasSpec(String modId, String name, int tintArgb, String descriptionId) {
        this(modId, name, tintArgb, descriptionId, 0, DEFAULT_TICK_INTERVAL);
    }

    public static String defaultDescriptionId(String modId, String name) {
        return "fluid." + modId + ".gas_fluid_" + name;
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
