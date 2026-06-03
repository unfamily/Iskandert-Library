package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.unfamily.iskalib.migration.worldbackup.packet.WorldBackupPromptS2CPacket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WorldBackupGateHandler {
    private final Map<UUID, Set<String>> gatedPlayers = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!WorldBackupGate.isEnabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (WorldBackupGateConfig config : WorldBackupGate.configs()) {
            WorldBackupGate.autoAcknowledgeFreshWorlds(server, config);
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyGatesForPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WorldBackupGate.isEnabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyGatesForPlayer(player);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isGated(player)) {
            return;
        }
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;
        if (player.fallDistance > 0) {
            player.fallDistance = 0;
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isGated(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isGated(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isGated(player)) {
            event.setCanceled(true);
        }
    }

    boolean isGated(ServerPlayer player) {
        Set<String> gates = gatedPlayers.get(player.getUUID());
        return gates != null && !gates.isEmpty();
    }

    void releasePlayer(ServerPlayer player, String registryKey) {
        Set<String> gates = gatedPlayers.get(player.getUUID());
        if (gates != null) {
            gates.remove(registryKey);
            if (gates.isEmpty()) {
                gatedPlayers.remove(player.getUUID());
            }
        }
    }

    void releaseAllPlayers(MinecraftServer server) {
        gatedPlayers.clear();
    }

    private void applyGatesForPlayer(ServerPlayer player) {
        for (WorldBackupGateConfig config : WorldBackupGate.configs()) {
            if (WorldBackupGate.requiresBackupPrompt((ServerLevel) player.level(), config)) {
                gatePlayer(player, config.registryKey());
            }
        }
    }

    private void gatePlayer(ServerPlayer player, String registryKey) {
        gatedPlayers.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet()).add(registryKey);
        if (player.level().getServer() != null) {
            player.level().getServer().execute(() ->
                    PacketDistributor.sendToPlayer(player, new WorldBackupPromptS2CPacket(registryKey)));
        }
    }
}
