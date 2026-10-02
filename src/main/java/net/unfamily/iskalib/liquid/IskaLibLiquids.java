package net.unfamily.iskalib.liquid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.iskalib.IskaLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public API: consumer mods register normal liquids on their mod event bus.
 * Mirrors {@link net.unfamily.iskalib.gas.IskaLibGases} and Colossal Reactors {@code ModFluids.registerTintedFluid}.
 * <p>
 * {@link LiquidSpec#modId()} must be the <strong>hosting mod</strong> id (the consumer that owns the
 * DeferredRegisters), not {@code iska_lib}, unless you are registering a Library-owned liquid via
 * {@link #registerLibraryLiquid}.
 * <p>
 * Pass the consumer's existing {@link DeferredRegister} instances via {@link LiquidRegistrationRegisters}.
 * Runtime infinity / loggable / dimension-tick overlays come from {@link LiquidBehaviorRegistry}
 * (see {@link OverlayAwareFluidType} and {@link DimensionTickLiquidBlock}).
 * <p>
 * Library also owns registers for fluids declared under {@code data/iska_lib/iska_lib/liquids/}
 * (see {@link LiquidJsonLoader#bootstrapAndRegister}).
 */
public final class IskaLibLiquids {
    private static final Map<String, ModLiquidRegistration> BY_MOD = new ConcurrentHashMap<>();
    private static IEventBus libraryModEventBus;
    private static boolean libraryRegistersReady;

    private IskaLibLiquids() {}

    public static void initLibrary(IEventBus iskaLibModEventBus) {
        libraryModEventBus = iskaLibModEventBus;
        IskaLibOwnedLiquidRegisters.register(iskaLibModEventBus);
        libraryRegistersReady = true;
    }

    /**
     * Registers a liquid owned by {@code iska_lib} using Library deferred registers.
     * Used by {@link LiquidJsonLoader} for jar-scoped JSON fluids.
     */
    public static RegisteredLiquid registerLibraryLiquid(LiquidSpec spec) {
        if (!libraryRegistersReady || libraryModEventBus == null) {
            throw new IllegalStateException("IskaLibLiquids.initLibrary must run before registerLibraryLiquid");
        }
        if (!IskaLib.MOD_ID.equals(spec.modId())) {
            throw new IllegalArgumentException("Library-owned liquids must use modId " + IskaLib.MOD_ID + ", got " + spec.modId());
        }
        return registerLiquid(libraryModEventBus, IskaLibOwnedLiquidRegisters.asLiquidRegisters(), spec);
    }

    public static boolean isRegistered(ResourceLocation fluidId) {
        if (fluidId == null) {
            return false;
        }
        for (RegisteredLiquid liquid : allRegisteredLiquids()) {
            if (fluidId.equals(liquid.sourceFluidId())) {
                return true;
            }
        }
        return false;
    }

    private static void hookClientEventsOnce(IEventBus modEventBus) {
        net.unfamily.iskalib.client.IskaLibFluidClient.hookConsumerModClientOnce(modEventBus);
    }

    /**
     * Registers a tinted liquid using shared {@code block/fluid/still|flow} sprites (Colossal Reactors convention).
     */
    public static RegisteredLiquid registerLiquid(
            IEventBus modEventBus,
            LiquidRegistrationRegisters registers,
            String modId,
            String name,
            int tintArgb
    ) {
        return registerLiquid(modEventBus, registers, new LiquidSpec(modId, name, tintArgb));
    }

    public static RegisteredLiquid registerLiquid(
            IEventBus modEventBus,
            LiquidRegistrationRegisters registers,
            String modId,
            String name,
            int tintArgb,
            String descriptionId,
            int lightLevel
    ) {
        return registerLiquid(modEventBus, registers, new LiquidSpec(modId, name, tintArgb, descriptionId, lightLevel));
    }

    /**
     * Registers a consumer-owned liquid. {@code spec.modId()} must match {@code registers} namespace
     * (hosting mod), not {@code iska_lib}.
     */
    public static RegisteredLiquid registerLiquid(IEventBus modEventBus, LiquidRegistrationRegisters registers, LiquidSpec spec) {
        hookClientEventsOnce(modEventBus);
        ModLiquidRegistration reg = BY_MOD.computeIfAbsent(spec.modId(), id -> new ModLiquidRegistration(registers));
        return reg.register(spec);
    }

    public static List<RegisteredLiquid> allRegisteredLiquids() {
        List<RegisteredLiquid> out = new ArrayList<>();
        for (ModLiquidRegistration reg : BY_MOD.values()) {
            out.addAll(reg.registeredLiquids());
        }
        return out;
    }

    private static final class ModLiquidRegistration {
        private final String modId;
        private final DeferredRegister<FluidType> fluidTypes;
        private final DeferredRegister<Fluid> fluids;
        private final DeferredRegister.Blocks blocks;
        private final DeferredRegister.Items items;
        private final List<RegisteredLiquid> registered = new ArrayList<>();

        ModLiquidRegistration(LiquidRegistrationRegisters registers) {
            this.modId = registers.blocks().getNamespace();
            this.fluidTypes = registers.fluidTypes();
            this.fluids = registers.fluids();
            this.blocks = registers.blocks();
            this.items = registers.items();
        }

        RegisteredLiquid register(LiquidSpec spec) {
            if (!spec.modId().equals(modId)) {
                throw new IllegalArgumentException("LiquidSpec modId " + spec.modId() + " does not match registration modId " + modId);
            }

            LiquidTypeProperties typeProperties = spec.typeProperties();
            if (spec.blockProperties().loggable()) {
                typeProperties = typeProperties.withLoggableDefaults();
            }
            LiquidTypeProperties finalTypeProperties = typeProperties;

            var refs = new Object() {
                DeferredHolder<FluidType, FluidType> fluidType;
                DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
                DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
                DeferredBlock<LiquidBlock> block;
                DeferredHolder<Item, net.minecraft.world.item.BucketItem> bucket;
            };

            ResourceLocation sourceFluidId = ResourceLocation.fromNamespaceAndPath(modId, spec.fluidSourceId());
            ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(modId, spec.blockId());
            ResourceLocation bucketId = ResourceLocation.fromNamespaceAndPath(modId, spec.bucketId());

            refs.fluidType = fluidTypes.register(spec.fluidSourceId() + "_type", () -> new OverlayAwareFluidType(
                    finalTypeProperties.build(spec.descriptionId(), spec.lightLevel(), spec.sounds()),
                    sourceFluidId,
                    finalTypeProperties.canConvertToSource(),
                    finalTypeProperties.canHydrate(),
                    finalTypeProperties.supportsBoating()));

            BaseFlowingFluid.Properties fluidProps = new BaseFlowingFluid.Properties(
                    refs.fluidType,
                    () -> refs.source.get(),
                    () -> refs.flowing.get())
                    .block(() -> refs.block.get())
                    .bucket(() -> spec.registerBucket() && refs.bucket.isBound() ? refs.bucket.get() : null);
            spec.flowProperties().applyTo(fluidProps);

            refs.source = fluids.register(spec.fluidSourceId(), () -> new BaseFlowingFluid.Source(fluidProps));
            refs.flowing = fluids.register(spec.fluidFlowingId(), () -> new BaseFlowingFluid.Flowing(fluidProps));

            LiquidBlockProperties blockProps = spec.blockProperties();
            LiquidBlockFactory factory = blockProps.blockFactory();
            // Always use DimensionTickLiquidBlock when no custom factory so datapack overlays can add
            // dimension_ticks later without requiring a DimensionTickLiquidBlock at Java registration time.
            if (factory == null) {
                List<DimensionTickTransform> transforms = spec.dimensionTicks();
                factory = (flowing, props) -> new DimensionTickLiquidBlock(flowing, props, sourceFluidId, transforms);
            }
            LiquidBlockFactory finalFactory = factory;

            refs.block = blocks.register(spec.blockId(), () -> {
                var props = blockProps.toBlockProperties(spec.lightLevel());
                if (finalFactory != null) {
                    return finalFactory.create(refs.flowing.get(), props);
                }
                return blockProps.createBlock(refs.flowing.get(), props);
            });

            if (spec.registerBucket()) {
                refs.bucket = items.register(spec.bucketId(), () -> new net.minecraft.world.item.BucketItem(
                        refs.source.get(),
                        new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
            }

            RegisteredLiquid liquid = new RegisteredLiquid(
                    spec,
                    refs.source,
                    refs.flowing,
                    refs.block,
                    refs.fluidType,
                    () -> refs.bucket,
                    sourceFluidId,
                    blockId,
                    bucketId);

            registered.add(liquid);
            LiquidRegistry.register(liquid);

            LiquidBehaviorRegistry.put(sourceFluidId, new LiquidBehaviorRegistry.Overlay(
                    finalTypeProperties.canConvertToSource(),
                    blockProps.loggable(),
                    spec.dimensionTicks()));

            return liquid;
        }

        List<RegisteredLiquid> registeredLiquids() {
            return List.copyOf(registered);
        }
    }
}
