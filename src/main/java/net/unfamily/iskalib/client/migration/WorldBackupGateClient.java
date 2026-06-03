package net.unfamily.iskalib.client.migration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGate;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGateConfig;
import net.unfamily.iskalib.migration.worldbackup.packet.WorldBackupResponseC2SPacket;

/**
 * Client UI for {@link net.unfamily.iskalib.migration.worldbackup.WorldBackupGate} (vanilla-style confirm screen).
 */
public final class WorldBackupGateClient {
    private static boolean promptVisible;

    private WorldBackupGateClient() {
    }

    public static boolean isPromptVisible() {
        return promptVisible;
    }

    public static void openPromptScreen(String registryKey) {
        WorldBackupGateConfig config = WorldBackupGate.getConfig(registryKey);
        if (config == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        promptVisible = true;
        minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    promptVisible = false;
                    minecraft.setScreen(null);
                    ClientPacketDistributor.sendToServer(new WorldBackupResponseC2SPacket(registryKey, confirmed));
                },
                Component.translatable(config.titleKey()),
                Component.translatable(config.warningKey(), config.migrationVersionLabel()),
                Component.translatable(config.confirmKey()),
                Component.translatable(config.cancelKey())
        ));
    }
}
