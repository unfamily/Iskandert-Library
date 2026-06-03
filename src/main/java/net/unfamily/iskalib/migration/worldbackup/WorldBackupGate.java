package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.unfamily.iskalib.IskaLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable one-time backup gate: host mods call {@link #install(IEventBus, WorldBackupGateConfig)} from common setup.
 */
public final class WorldBackupGate {
    private static final Map<String, WorldBackupGateConfig> CONFIGS = new ConcurrentHashMap<>();
    private static boolean initialized;
    private static WorldBackupGateHandler handler;

    private WorldBackupGate() {
    }

    public static void install(IEventBus modEventBus, WorldBackupGateConfig config) {
        CONFIGS.put(config.registryKey(), config);
        if (!initialized) {
            modEventBus.addListener(WorldBackupGateNetwork::registerPayloads);
            handler = new WorldBackupGateHandler();
            NeoForge.EVENT_BUS.register(handler);
            initialized = true;
            IskaLib.LOGGER.info("World backup gate framework enabled");
        }
        IskaLib.LOGGER.info("Registered world backup gate {} for host mod {}", config.gateId(), config.hostModId());
    }

    public static boolean isEnabled() {
        return !CONFIGS.isEmpty();
    }

    public static Collection<WorldBackupGateConfig> configs() {
        return Collections.unmodifiableCollection(CONFIGS.values());
    }

    public static WorldBackupGateConfig getConfig(String registryKey) {
        return CONFIGS.get(registryKey);
    }

    static WorldBackupGateHandler handler() {
        return handler;
    }

    public static String currentHostModVersion(WorldBackupGateConfig config) {
        return ModList.get()
                .getModContainerById(config.hostModId())
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(config.migrationVersionLabel());
    }

    public static boolean hasLegacyWorldData(MinecraftServer server, WorldBackupGateConfig config) {
        if (config.legacyWorldDataFileNames().isEmpty()) {
            return false;
        }
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return false;
        }
        for (String fileName : config.legacyWorldDataFileNames()) {
            if (Files.exists(dataDir.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLegacyAckOnDisk(MinecraftServer server, WorldBackupGateConfig config) {
        if (!config.hasLegacyAckMigration()) {
            return false;
        }
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(config.legacyAckSavedDataName() + ".dat");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(1048576L));
            return root != null && root.getBoolean(config.legacyAckNbtKey()).orElse(false);
        } catch (IOException e) {
            IskaLib.LOGGER.warn("Failed to read legacy backup ack from {}: {}", path, e.getMessage());
            return false;
        }
    }

    public static boolean isAcknowledged(ServerLevel level, WorldBackupGateConfig config) {
        WorldBackupGateMetadata metadata = WorldBackupGateMetadata.get(level);
        if (metadata.isAcknowledged(config.registryKey())) {
            return true;
        }
        MinecraftServer server = level.getServer();
        if (server != null && hasLegacyAckOnDisk(server, config)) {
            metadata.acknowledge(config.registryKey(), currentHostModVersion(config));
            return true;
        }
        return false;
    }

    public static boolean requiresBackupPrompt(ServerLevel level, WorldBackupGateConfig config) {
        if (isAcknowledged(level, config)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        return server != null && hasLegacyWorldData(server, config);
    }

    public static void autoAcknowledgeFreshWorlds(MinecraftServer server, WorldBackupGateConfig config) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        if (isAcknowledged(overworld, config)) {
            return;
        }
        if (!hasLegacyWorldData(server, config)) {
            WorldBackupGateMetadata.get(overworld).acknowledge(config.registryKey(), currentHostModVersion(config));
        }
    }

    public static void acknowledgeWorld(ServerLevel level, WorldBackupGateConfig config) {
        WorldBackupGateMetadata.get(level).acknowledge(config.registryKey(), currentHostModVersion(config));
    }

    public static void onPlayerResponse(ServerPlayer player, String registryKey, boolean accepted) {
        WorldBackupGateConfig config = getConfig(registryKey);
        if (config == null || handler == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!requiresBackupPrompt(level, config)) {
            handler.releasePlayer(player, registryKey);
            return;
        }
        if (accepted) {
            acknowledgeWorld(level, config);
            if (player.level().getServer() != null) {
                handler.releaseAllPlayers(player.level().getServer());
            }
        } else {
            player.connection.disconnect(Component.translatable(config.declinedKey()));
        }
    }
}
