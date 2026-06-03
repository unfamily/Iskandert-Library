package net.unfamily.iskalib.gas;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.SoundActions;
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
 * Public API: consumer mods register gases on their mod event bus.
 * <p>
 * Pass the consumer's existing {@link DeferredRegister} instances via {@link GasRegistrationRegisters}
 * so blocks/items/fluids share one registrar per namespace. Do not create a second {@code DeferredRegister}
 * for the same mod id.
 */
public final class IskaLibGases {
    private static final Map<String, ModGasRegistration> BY_MOD = new ConcurrentHashMap<>();
    private static final java.util.Set<IEventBus> CLIENT_HOOKED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean interactionsInitialized;

    private IskaLibGases() {}

    /** Called from {@link net.unfamily.iskalib.IskaLib} to wire global interaction handlers. */
    public static void initLibrary(IEventBus iskaLibModEventBus) {
        ensureInteractions(iskaLibModEventBus);
    }

    private static void hookClientEventsOnce(IEventBus modEventBus) {
        if (!isPhysicalClient() || !CLIENT_HOOKED.add(modEventBus)) {
            return;
        }
        modEventBus.addListener(net.unfamily.iskalib.client.IskaLibFluidClient::registerClientExtensions);
        modEventBus.addListener(net.unfamily.iskalib.client.IskaLibFluidClient::registerBlockColors);
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

            refs.fluidType = fluidTypes.register(spec.fluidSourceId() + "_type", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId(spec.descriptionId())
                    .lightLevel(spec.lightLevel())
                    .density(-1000)
                    .viscosity(100)
                    .temperature(300)
                    .canDrown(false)
                    .canSwim(false)
                    .canPushEntity(false)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

            AtomicReference<RegisteredGas> gasRef = new AtomicReference<>();

            // Fluids and block before bucket (same order as ModFluids). Bucket must not be .get() during item/fluid construction.
            BaseFlowingFluid.Properties fluidProps = new BaseFlowingFluid.Properties(
                    refs.fluidType,
                    () -> refs.source.get(),
                    () -> refs.flowing.get())
                    .block(() -> refs.block.get())
                    .bucket(() -> refs.bucket.isBound() ? refs.bucket.get() : null)
                    .tickRate(Integer.MAX_VALUE / 4)
                    .slopeFindDistance(0)
                    .levelDecreasePerBlock(Integer.MAX_VALUE / 4);

            refs.source = fluids.register(spec.fluidSourceId(), () -> new GasFlowingFluid.Source(fluidProps));
            refs.flowing = fluids.register(spec.fluidFlowingId(), () -> new GasFlowingFluid.Flowing(fluidProps));

            ResourceLocation sourceFluidId = ResourceLocation.fromNamespaceAndPath(modId, spec.fluidSourceId());
            ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(modId, spec.blockId());
            ResourceLocation bucketId = ResourceLocation.fromNamespaceAndPath(modId, spec.bucketId());

            refs.block = blocks.register(spec.blockId(), () -> new GasLiquidBlock(
                    refs.source.get(),
                    GasLiquidBlock.configureProperties(spec.lightLevel()),
                    gasRef::get,
                    spec.tickInterval()));

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

            refs.bucket = items.register(spec.bucketId(), () -> new GasBucketItem(
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1),
                    gasRef.get(),
                    refs.source));

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
