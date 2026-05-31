package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
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

/**
 * Public API: consumer mods register normal liquids on their mod event bus (NeoForge 26.1.2+).
 * Mirrors {@link net.unfamily.iskalib.gas.IskaLibGases} and Colossal Reactors {@code ModFluids.registerTintedFluid}.
 * <p>
 * Pass the consumer's existing {@link DeferredRegister} instances via {@link LiquidRegistrationRegisters}.
 * Not for Minecraft 1.21.1 — consumers on older lines ship an in-mod copy (see IskaUtils {@code ModFluids}).
 */
public final class IskaLibLiquids {
    private static final Map<String, ModLiquidRegistration> BY_MOD = new ConcurrentHashMap<>();
    private static final java.util.Set<IEventBus> CLIENT_HOOKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private IskaLibLiquids() {}

    public static void initLibrary(IEventBus iskaLibModEventBus) {
        // Reserved for global hooks if needed later.
    }

    private static void hookClientEventsOnce(IEventBus modEventBus) {
        if (!isPhysicalClient() || !CLIENT_HOOKED.add(modEventBus)) {
            return;
        }
        modEventBus.addListener(net.unfamily.iskalib.client.liquid.IskaLibLiquidFluidModels::registerFluidModels);
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

            var refs = new Object() {
                DeferredHolder<FluidType, FluidType> fluidType;
                DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
                DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
                DeferredBlock<LiquidBlock> block;
                DeferredHolder<Item, net.minecraft.world.item.BucketItem> bucket;
            };

            refs.fluidType = fluidTypes.register(spec.fluidSourceId() + "_type", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId(spec.descriptionId())
                    .lightLevel(spec.lightLevel())
                    .density(1000)
                    .viscosity(1000)
                    .temperature(300)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

            BaseFlowingFluid.Properties fluidProps = new BaseFlowingFluid.Properties(
                    refs.fluidType,
                    () -> refs.source.get(),
                    () -> refs.flowing.get())
                    .block(() -> refs.block.get())
                    .bucket(() -> spec.registerBucket() && refs.bucket.isBound() ? refs.bucket.get() : null);

            refs.source = fluids.register(spec.fluidSourceId(), () -> new BaseFlowingFluid.Source(fluidProps));
            refs.flowing = fluids.register(spec.fluidFlowingId(), () -> new BaseFlowingFluid.Flowing(fluidProps));

            Identifier sourceFluidId = Identifier.fromNamespaceAndPath(modId, spec.fluidSourceId());
            Identifier blockId = Identifier.fromNamespaceAndPath(modId, spec.blockId());
            Identifier bucketId = Identifier.fromNamespaceAndPath(modId, spec.bucketId());

            refs.block = blocks.registerBlock(spec.blockId(),
                    props -> new LiquidBlock(refs.source.get(), props),
                    props -> props.mapColor(MapColor.COLOR_GRAY)
                            .replaceable()
                            .strength(100.0F)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()
                            .liquid()
                            .lightLevel(state -> spec.lightLevel()));

            if (spec.registerBucket()) {
                refs.bucket = items.registerItem(spec.bucketId(),
                        props -> new net.minecraft.world.item.BucketItem(refs.source.get(), props),
                        () -> new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
            }

            RegisteredLiquid liquid = new RegisteredLiquid(
                    spec,
                    refs.source,
                    refs.flowing,
                    refs.block,
                    () -> refs.bucket,
                    sourceFluidId,
                    blockId,
                    bucketId);

            registered.add(liquid);
            LiquidRegistry.register(liquid);
            return liquid;
        }

        List<RegisteredLiquid> registeredLiquids() {
            return List.copyOf(registered);
        }
    }
}
