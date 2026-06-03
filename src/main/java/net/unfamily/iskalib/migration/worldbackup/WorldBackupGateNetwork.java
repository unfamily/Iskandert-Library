package net.unfamily.iskalib.migration.worldbackup;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.migration.worldbackup.packet.WorldBackupPromptS2CPacket;
import net.unfamily.iskalib.migration.worldbackup.packet.WorldBackupResponseC2SPacket;

final class WorldBackupGateNetwork {
    private WorldBackupGateNetwork() {
    }

    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(IskaLib.MOD_ID).versioned("1");
        registrar.playToClient(
                WorldBackupPromptS2CPacket.TYPE,
                WorldBackupPromptS2CPacket.STREAM_CODEC,
                WorldBackupPromptS2CPacket::handle);
        registrar.playToServer(
                WorldBackupResponseC2SPacket.TYPE,
                WorldBackupResponseC2SPacket.STREAM_CODEC,
                WorldBackupResponseC2SPacket::handle);
    }
}
