package net.unfamily.iskalib.gas;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
 */
public final class IskaLibGases {
    private static final Map<String, ModGasRegistration> BY_MOD = new ConcurrentHashMap<>();
    private static final java.util.Set<IEventBus> CLIENT_HOOKED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean interactionsInitialized;

    private IskaLibGases() {}

    /** Called from {@link net.unfamily.iskalib.IskaLib} to wire global interaction handlers. */
    public static void initLibrary(IEventBus modEventBus) {
        ensureInteractions();
    }

    private static void hookClientEventsOnce(IEventBus modEventBus) {
        if (!isPhysicalClient() || !CLIENT_HOOKED.add(modEventBus)) {
            return;
        }
        modEventBus.addListener(net.unfamily.iskalib.client.gas.IskaLibGasFluidModels::registerFluidModels);
        modEventBus.addListener(net.unfamily.iskalib.client.gas.IskaLibGasBlockModels::registerBlockTintSources);
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
     * Queue and register a gas type under {@code modId} with naming convention {@code gas_fluid_{name}}, {@code gas_{name}}, bucket.
     */
    public static RegisteredGas registerGas(IEventBus modEventBus, String modId, String name, int tintArgb) {
        return registerGas(modEventBus, new GasSpec(modId, name, tintArgb));
    }

    public static RegisteredGas registerGas(IEventBus modEventBus, GasSpec spec) {
        ensureInteractions();
        hookClientEventsOnce(modEventBus);
        ModGasRegistration reg = BY_MOD.computeIfAbsent(spec.modId(), id -> {
            ModGasRegistration created = new ModGasRegistration(id);
            created.attach(modEventBus);
            return created;
        });
        return reg.register(spec);
    }

    private static void ensureInteractions() {
        if (!interactionsInitialized) {
            interactionsInitialized = true;
            NeoForge.EVENT_BUS.addListener(GasFluidInteractions::onRegisterCapabilities);
            NeoForge.EVENT_BUS.addListener(GasFluidInteractions::onRightClickBlock);
        }
    }

    private static final class ModGasRegistration {
        private final String modId;
        private final DeferredRegister<FluidType> fluidTypes;
        private final DeferredRegister<Fluid> fluids;
        private final DeferredRegister.Blocks blocks;
        private final DeferredRegister.Items items;
        private final List<RegisteredGas> registered = new ArrayList<>();

        ModGasRegistration(String modId) {
            this.modId = modId;
            this.fluidTypes = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, modId);
            this.fluids = DeferredRegister.create(BuiltInRegistries.FLUID, modId);
            this.blocks = DeferredRegister.createBlocks(modId);
            this.items = DeferredRegister.createItems(modId);
        }

        void attach(IEventBus bus) {
            fluidTypes.register(bus);
            fluids.register(bus);
            blocks.register(bus);
            items.register(bus);
            // RegisterCapabilitiesEvent is NeoForge-global only — wired in ensureInteractions().
        }

        RegisteredGas register(GasSpec spec) {
            if (!spec.modId().equals(modId)) {
                throw new IllegalArgumentException("GasSpec modId " + spec.modId() + " does not match registration modId " + modId);
            }

            var refs = new Object() {
                DeferredHolder<FluidType, FluidType> fluidType;
                DeferredHolder<Fluid, GasFlowingFluid.Source> source;
                DeferredHolder<Fluid, GasFlowingFluid.Flowing> flowing;
                DeferredBlock<GasBlock> block;
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

            BaseFlowingFluid.Properties fluidProps = new BaseFlowingFluid.Properties(
                    refs.fluidType,
                    () -> refs.source.get(),
                    () -> refs.flowing.get())
                    .bucket(() -> refs.bucket.get());

            refs.source = fluids.register(spec.fluidSourceId(), () -> new GasFlowingFluid.Source(fluidProps));
            refs.flowing = fluids.register(spec.fluidFlowingId(), () -> new GasFlowingFluid.Flowing(fluidProps));

            refs.block = blocks.register(spec.blockId(), () -> new GasBlock(
                    GasBlock.defaultProperties(spec.lightLevel()),
                    gasRef::get,
                    spec.tickInterval()));

            refs.bucket = items.register(spec.bucketId(), () -> new GasBucketItem(
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1),
                    gasRef.get()));

            Identifier sourceFluidId = Identifier.fromNamespaceAndPath(modId, spec.fluidSourceId());
            Identifier blockId = Identifier.fromNamespaceAndPath(modId, spec.blockId());
            Identifier bucketId = Identifier.fromNamespaceAndPath(modId, spec.bucketId());

            RegisteredGas gas = new RegisteredGas(
                    spec,
                    refs.source,
                    refs.flowing,
                    refs.block,
                    refs.bucket,
                    sourceFluidId,
                    blockId,
                    bucketId);
            gasRef.set(gas);

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
