package net.unfamily.iskalib;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.iskalib.gas.IskaLibGases;
import net.unfamily.iskalib.gas.RegisteredGas;
import net.unfamily.iskalib.liquid.IskaLibLiquids;
import net.unfamily.iskalib.liquid.RegisteredLiquid;

/**
 * Creative tab for Library-owned JSON fluids/gases. Registered only when at least one bucket exists.
 */
public final class IskaLibCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IskaLib.MOD_ID);

    private IskaLibCreativeTabs() {}

    public static void registerIfNeeded(IEventBus modEventBus) {
        if (!hasAnyDisplayItem()) {
            return;
        }
        TABS.register("fluids", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.iska_lib.fluids"))
                .icon(IskaLibCreativeTabs::firstIcon)
                .displayItems((params, output) -> {
                    for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
                        if (!liquid.spec().registerBucket()) {
                            continue;
                        }
                        try {
                            output.accept(liquid.bucketItem());
                        } catch (RuntimeException ignored) {
                            // Bucket deferred holder not ready / absent
                        }
                    }
                    for (RegisteredGas gas : IskaLibGases.allRegisteredGases()) {
                        try {
                            output.accept(gas.bucketItem());
                        } catch (RuntimeException ignored) {
                            // ignore
                        }
                    }
                })
                .build());
        TABS.register(modEventBus);
    }

    private static boolean hasAnyDisplayItem() {
        for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
            if (liquid.spec().registerBucket()) {
                return true;
            }
        }
        return !IskaLibGases.allRegisteredGases().isEmpty();
    }

    private static ItemStack firstIcon() {
        for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
            if (!liquid.spec().registerBucket()) {
                continue;
            }
            try {
                Item bucket = liquid.bucketItem();
                if (bucket != null && bucket != Items.AIR) {
                    return new ItemStack(bucket);
                }
            } catch (RuntimeException ignored) {
                // try next
            }
        }
        for (RegisteredGas gas : IskaLibGases.allRegisteredGases()) {
            try {
                Item bucket = gas.bucketItem();
                if (bucket != null && bucket != Items.AIR) {
                    return new ItemStack(bucket);
                }
            } catch (RuntimeException ignored) {
                // try next
            }
        }
        return new ItemStack(Items.BUCKET);
    }
}
