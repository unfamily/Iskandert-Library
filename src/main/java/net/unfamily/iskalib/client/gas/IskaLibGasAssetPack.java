package net.unfamily.iskalib.client.gas;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.unfamily.iskalib.gas.LibraryGasGeneratedAssets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Exposes generated gas blockstates as a built-in client resource pack.
 */
public final class IskaLibGasAssetPack {
    private static final String PACK_ID = "iska_lib/generated_gases";

    private IskaLibGasAssetPack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = LibraryGasGeneratedAssets.assetsRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(
                        PACK_ID,
                        Component.literal("IskaLib Generated Gas Models"),
                        PackSource.BUILT_IN,
                        Optional.empty()),
                new PathPackResources.PathResourcesSupplier(root),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
