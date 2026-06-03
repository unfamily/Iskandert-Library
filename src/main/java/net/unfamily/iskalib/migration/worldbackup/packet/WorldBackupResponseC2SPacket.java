package net.unfamily.iskalib.migration.worldbackup.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGate;
import net.unfamily.iskalib.migration.worldbackup.WorldBackupGateConfig;

public record WorldBackupResponseC2SPacket(String registryKey, boolean accepted) implements CustomPacketPayload {

    public static final Type<WorldBackupResponseC2SPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "world_backup_response"));

    public static final StreamCodec<FriendlyByteBuf, WorldBackupResponseC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WorldBackupResponseC2SPacket::registryKey,
                    ByteBufCodecs.BOOL, WorldBackupResponseC2SPacket::accepted,
                    WorldBackupResponseC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorldBackupResponseC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            WorldBackupGate.onPlayerResponse(player, packet.registryKey(), packet.accepted());
        });
    }
}
