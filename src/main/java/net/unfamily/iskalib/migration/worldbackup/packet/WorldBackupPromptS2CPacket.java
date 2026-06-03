package net.unfamily.iskalib.migration.worldbackup.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.client.migration.WorldBackupGateClient;

public record WorldBackupPromptS2CPacket(String registryKey) implements CustomPacketPayload {

    public static final Type<WorldBackupPromptS2CPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "world_backup_prompt"));

    public static final StreamCodec<FriendlyByteBuf, WorldBackupPromptS2CPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, WorldBackupPromptS2CPacket::registryKey,
                    WorldBackupPromptS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorldBackupPromptS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> WorldBackupGateClient.openPromptScreen(packet.registryKey()));
    }
}
