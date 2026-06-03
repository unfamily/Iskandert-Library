package net.unfamily.iskalib.client.migration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGate;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGateConfig;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGateStorage;

import java.nio.file.Path;

/**
 * Pre-world-load backup UI using vanilla {@link BackupConfirmScreen} (backup yes / continue without).
 */
public final class WorldBackupGateClient {
    private WorldBackupGateClient() {
    }

    public static void showPreWorldLoadBackupScreen(
            Minecraft minecraft,
            LevelStorageSource.LevelStorageAccess access,
            WorldBackupGateConfig config,
            Runnable onProceed,
            Runnable onCancel) {
        Path dataDir = WorldBackupGateStorage.worldDataDir(access);
        minecraft.setScreen(new BackupConfirmScreen(
                onCancel,
                (makeBackup, ignored) -> {
                    WorldBackupGate.acknowledgeOnDisk(dataDir, config);
                    if (makeBackup) {
                        EditWorldScreen.makeBackupAndShowToast(access);
                    }
                    onProceed.run();
                },
                Component.translatable(config.titleKey()),
                Component.translatable(config.warningKey(), config.migrationVersionLabel()),
                false));
    }
}
