package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;

/**
 * Immutable registration request for one normal liquid in a consumer mod namespace.
 * IDs follow Colossal Reactors {@code ModFluids}: {@code name}, {@code name_flowing}, block {@code name}, bucket {@code name_bucket}.
 */
public record LiquidSpec(
        String modId,
        String name,
        int tintArgb,
        String descriptionId,
        int lightLevel,
        Identifier stillTexture,
        Identifier flowingTexture,
        boolean registerBucket
) {
    /** Shared molten-style sprites under {@code textures/block/fluid/still|flow.png} (Colossal Reactors convention). */
    public static final String DEFAULT_STILL_PATH = "block/fluid/still";
    public static final String DEFAULT_FLOWING_PATH = "block/fluid/flow";

    public LiquidSpec(String modId, String name, int tintArgb) {
        this(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, true);
    }

    public LiquidSpec(String modId, String name, int tintArgb, String descriptionId) {
        this(modId, name, tintArgb, descriptionId, 0, true);
    }

    public LiquidSpec(String modId, String name, int tintArgb, String descriptionId, int lightLevel) {
        this(modId, name, tintArgb, descriptionId, lightLevel, true);
    }

    public LiquidSpec(String modId, String name, int tintArgb, String descriptionId, int lightLevel, boolean registerBucket) {
        this(
                modId,
                name,
                tintArgb,
                descriptionId,
                lightLevel,
                Identifier.fromNamespaceAndPath(modId, DEFAULT_STILL_PATH),
                Identifier.fromNamespaceAndPath(modId, DEFAULT_FLOWING_PATH),
                registerBucket
        );
    }

    public LiquidSpec(String modId, String name, int tintArgb, String stillPath, String flowingPath) {
        this(
                modId,
                name,
                tintArgb,
                defaultDescriptionId(modId, name),
                0,
                Identifier.fromNamespaceAndPath(modId, stillPath),
                Identifier.fromNamespaceAndPath(modId, flowingPath),
                true
        );
    }

    public static String defaultDescriptionId(String modId, String name) {
        return "fluid." + modId + "." + name;
    }

    /** Source fluid registry name (same as {@link #name()} when using base-name ids). */
    public String fluidSourceId() {
        return name;
    }

    public String fluidFlowingId() {
        return name + "_flowing";
    }

    public String blockId() {
        return name;
    }

    public String bucketId() {
        return name + "_bucket";
    }
}
