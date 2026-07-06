package net.unfamily.iskalib.gas;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Public API: consumer mods register gases on their mod event bus (NeoForge 26.1.2+ only).
 * <p>
 * Pass the consumer's existing {@link DeferredRegister} instances via {@link GasRegistrationRegisters}
 * so blocks/items/fluids share one registrar per namespace. Do not create a second {@code DeferredRegister}
 * for the same mod id.
 * <p>
 * Not for Minecraft 1.21.1 — consumers on older lines must ship an in-mod gas copy (see Colossal Reactors 1.21.1).
 */
public final class IskaLibGases {
    private static final Map<String, ModGasRegistration> BY_MOD = new ConcurrentHashMap<>();
    private static boolean interactionsInitialized;

    private IskaLibGases() {}

    /** Called from {@link net.unfamily.iskalib.IskaLib} to wire global interaction handlers. */
    public static void initLibrary(IEventBus iskaLibModEventBus) {
        ensureInteractions(iskaLibModEventBus);
    }

    private static void hookClientEventsOnce(IEventBus modEventBus) {
        net.unfamily.iskalib.client.IskaLibConsumerClientHooks.hookConsumerModClientOnce(modEventBus);
    }

    private static boolean isPhysicalClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Registers a gas using the consumer mod's deferred registers (call after those registers are created,
     * before or after {@code registers.blocks().register(modEventBus)} — entries are added to the same queues).
     */
    public static RegisteredGas registerGas(
            IEventBus modEventBus,
            GasRegistrationRegisters registers,
            String modId,
            String name,
            int tintArgb
    ) {
        return registerGas(modEventBus, registers, new GasSpec(modId, name, tintArgb));
    }

    public static RegisteredGas registerGas(IEventBus modEventBus, GasRegistrationRegisters registers, GasSpec spec) {
        hookClientEventsOnce(modEventBus);
        ModGasRegistration reg = BY_MOD.computeIfAbsent(spec.modId(), id -> new ModGasRegistration(registers));
        return reg.register(spec);
    }

    private static void ensureInteractions(IEventBus iskaLibModEventBus) {
        if (!interactionsInitialized) {
            interactionsInitialized = true;
            NeoForge.EVENT_BUS.addListener(GasFluidInteractions::onRightClickBlock);
        }
    }

    /**
     * Call from the consumer mod's {@link net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent}
     * (same mod bus as {@link GasRegistrationRegisters#blocks()}). Required for bucket/pipe extraction.
     */
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        GasFluidInteractions.onRegisterCapabilities(event);
    }

    private static final class ModGasRegistration {
        private final String modId;
        private final DeferredRegister<FluidType> fluidTypes;
        private final DeferredRegister<Fluid> fluids;
        private final DeferredRegister.Blocks blocks;
        private final DeferredRegister.Items items;
        private final List<RegisteredGas> registered = new ArrayList<>();

        ModGasRegistration(GasRegistrationRegisters registers) {
            this.modId = registers.blocks().getNamespace();
            this.fluidTypes = registers.fluidTypes();
            this.fluids = registers.fluids();
            this.blocks = registers.blocks();
            this.items = registers.items();
        }

        RegisteredGas register(GasSpec spec) {
            if (!spec.modId().equals(modId)) {
                throw new IllegalArgumentException("GasSpec modId " + spec.modId() + " does not match registration modId " + modId);
            }

            var refs = new Object() {
                DeferredHolder<FluidType, FluidType> fluidType;
                DeferredHolder<Fluid, GasFlowingFluid.Source> source;
                DeferredHolder<Fluid, GasFlowingFluid.Flowing> flowing;
                DeferredBlock<GasLiquidBlock> block;
                DeferredHolder<Item, GasBucketItem> bucket;
            };

            refs.fluidType = fluidTypes.register(spec.fluidSourceId() + "_type", () -> new FluidType(
                    spec.typeProperties().build(spec.descriptionId(), spec.lightLevel(), spec.sounds())));

            AtomicReference<RegisteredGas> gasRef = new AtomicReference<>();

            // Fluids and block before bucket (same order as ModFluids). Bucket must not be .get() during item/fluid construction.
            BaseFlowingFluid.Properties fluidProps = new BaseFlowingFluid.Properties(
                    refs.fluidType,
                    () -> refs.source.get(),
                    () -> refs.flowing.get())
                    .block(() -> refs.block.get())
                    .bucket(() -> refs.bucket.isBound() ? refs.bucket.get() : null);
            spec.flowProperties().applyTo(fluidProps);

            refs.source = fluids.register(spec.fluidSourceId(), () -> new GasFlowingFluid.Source(fluidProps));
            refs.flowing = fluids.register(spec.fluidFlowingId(), () -> new GasFlowingFluid.Flowing(fluidProps));

            Identifier sourceFluidId = Identifier.fromNamespaceAndPath(modId, spec.fluidSourceId());
            Identifier blockId = Identifier.fromNamespaceAndPath(modId, spec.blockId());
            Identifier bucketId = Identifier.fromNamespaceAndPath(modId, spec.bucketId());

            refs.block = blocks.registerBlock(spec.blockId(),
                    props -> new GasLiquidBlock(refs.source.get(), props, gasRef::get, spec.tickInterval()),
                    props -> GasLiquidBlock.configureProperties(props, spec.lightLevel()));

            RegisteredGas gas = new RegisteredGas(
                    spec,
                    refs.source,
                    refs.flowing,
                    refs.block,
                    refs.fluidType,
                    () -> refs.bucket,
                    sourceFluidId,
                    blockId,
                    bucketId);
            gasRef.set(gas);

            refs.bucket = items.registerItem(spec.bucketId(),
                    props -> new GasBucketItem(props, gasRef.get(), refs.source),
                    () -> new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));

            registered.add(gas);
            GasRegistry.register(gas);
            return gas;
        }

        List<RegisteredGas> registeredGases() {
            return List.copyOf(registered);
        }
    }

    static List<RegisteredGas> allRegisteredGases() {
        List<RegisteredGas> out = new ArrayList<>();
        for (ModGasRegistration reg : BY_MOD.values()) {
            out.addAll(reg.registeredGases());
        }
        return out;
    }
}
