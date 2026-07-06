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
        boolean registerBucket,
        LiquidTypeProperties typeProperties,
        FlowingFluidProperties flowProperties,
        LiquidBlockProperties blockProperties,
        LiquidClientProperties clientProperties,
        LiquidSoundSet sounds
) {
    public static final String ISKA_LIB_ID = "iska_lib";

    /** Consumer-local molten-style sprites (Colossal Reactors convention). */
    public static final String DEFAULT_STILL_PATH = "block/fluid/still";
    public static final String DEFAULT_FLOWING_PATH = "block/fluid/flow";

    /** Shared thick/molten animated sprites in iska_lib (see textures/block/fluid/thick_*.png). */
    public static final String LIB_THICK_STILL_PATH = "block/fluid/thick_still";
    public static final String LIB_THICK_FLOW_PATH = "block/fluid/thick_flow";

    public static final Identifier ISKA_LIB_THICK_STILL =
            Identifier.fromNamespaceAndPath(ISKA_LIB_ID, LIB_THICK_STILL_PATH);
    public static final Identifier ISKA_LIB_THICK_FLOW =
            Identifier.fromNamespaceAndPath(ISKA_LIB_ID, LIB_THICK_FLOW_PATH);

    /** Thin liquid style: vanilla water sprites + tint (Colossal gelid breezium convention). */
    public static final Identifier VANILLA_THIN_STILL =
            Identifier.withDefaultNamespace("block/water_still");
    public static final Identifier VANILLA_THIN_FLOW =
            Identifier.withDefaultNamespace("block/water_flow");

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

    public LiquidSpec(
            String modId,
            String name,
            int tintArgb,
            String descriptionId,
            int lightLevel,
            Identifier stillTexture,
            Identifier flowingTexture,
            boolean registerBucket
    ) {
        this(
                modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture, registerBucket,
                LiquidTypeProperties.DEFAULT,
                FlowingFluidProperties.DEFAULT,
                LiquidBlockProperties.STANDARD,
                LiquidClientProperties.NONE,
                LiquidSoundSet.DEFAULT
        );
    }

    public static String defaultDescriptionId(String modId, String name) {
        return "fluid." + modId + "." + name;
    }

    /** Thick/molten style using shared {@code iska_lib:block/fluid/thick_still|thick_flow} sprites. */
    public static LiquidSpec withThickLibrarySprites(String modId, String name, int tintArgb) {
        return withThickLibrarySprites(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, true);
    }

    public static LiquidSpec withThickLibrarySprites(
            String modId, String name, int tintArgb, String descriptionId, int lightLevel, boolean registerBucket) {
        return new LiquidSpec(
                modId, name, tintArgb, descriptionId, lightLevel,
                ISKA_LIB_THICK_STILL, ISKA_LIB_THICK_FLOW, registerBucket);
    }

    /** Thin style using vanilla water sprites + tint (gelid breezium convention). */
    public static LiquidSpec withThinVanillaWaterSprites(String modId, String name, int tintArgb) {
        return withThinVanillaWaterSprites(modId, name, tintArgb, defaultDescriptionId(modId, name), 0, true);
    }

    public static LiquidSpec withThinVanillaWaterSprites(
            String modId, String name, int tintArgb, String descriptionId, int lightLevel, boolean registerBucket) {
        return new LiquidSpec(
                modId, name, tintArgb, descriptionId, lightLevel,
                VANILLA_THIN_STILL, VANILLA_THIN_FLOW, registerBucket);
    }

    public LiquidSpec withTypeProperties(LiquidTypeProperties typeProperties) {
        return new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture,
                registerBucket, typeProperties, flowProperties, blockProperties, clientProperties, sounds);
    }

    public LiquidSpec withFlowProperties(FlowingFluidProperties flowProperties) {
        return new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture,
                registerBucket, typeProperties, flowProperties, blockProperties, clientProperties, sounds);
    }

    public LiquidSpec withBlockProperties(LiquidBlockProperties blockProperties) {
        return new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture,
                registerBucket, typeProperties, flowProperties, blockProperties, clientProperties, sounds);
    }

    public LiquidSpec withClientProperties(LiquidClientProperties clientProperties) {
        return new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture,
                registerBucket, typeProperties, flowProperties, blockProperties, clientProperties, sounds);
    }

    public LiquidSpec withOverlay(Identifier overlayTexture) {
        return withClientProperties(LiquidClientProperties.withOverlay(overlayTexture));
    }

    public LiquidSpec withSounds(LiquidSoundSet sounds) {
        return new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel, stillTexture, flowingTexture,
                registerBucket, typeProperties, flowProperties, blockProperties, clientProperties, sounds);
    }

    public LiquidSpec withMoltenType() {
        return withTypeProperties(LiquidTypeProperties.MOLTEN);
    }

    public LiquidSpec withColdWaterType() {
        return withTypeProperties(LiquidTypeProperties.COLD_WATER_LIKE);
    }

    public LiquidSpec withBlockFactory(LiquidBlockFactory factory) {
        return withBlockProperties(blockProperties.withBlockFactory(factory));
    }

    public LiquidSpec withBlockLightLevel(int blockLightLevel) {
        return withBlockProperties(blockProperties.withBlockLightLevel(blockLightLevel));
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
