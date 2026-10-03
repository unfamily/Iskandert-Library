package net.unfamily.iskalib.crafting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.unfamily.iskalib.IskaLib;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Registers the in-memory recipe-bundle bridge datapack and invalidates it on reload.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class RecipeBundleBridgeHooks {
    private RecipeBundleBridgeHooks() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(
                RecipeBundleVirtualPack.PACK_ID,
                Component.literal("IskaLib Recipe Bundle Bridge"),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo location) {
                return new RecipeBundleVirtualPack(location);
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return openPrimary(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(
                info,
                supplier,
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // Invalidate so the next RecipeManager prepare rebuilds overlays when listResources runs.
        RecipeBundleVirtualPack.invalidate();
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(
                    PreparationBarrier stage,
                    ResourceManager resourceManager,
                    ProfilerFiller preparationsProfiler,
                    ProfilerFiller reloadProfiler,
                    Executor backgroundExecutor,
                    Executor gameExecutor) {
                RecipeBundleVirtualPack.invalidate();
                return CompletableFuture.<Void>completedFuture(null)
                        .thenCompose(stage::wait)
                        .thenAcceptAsync(v -> {}, gameExecutor);
            }
        });
    }
}
