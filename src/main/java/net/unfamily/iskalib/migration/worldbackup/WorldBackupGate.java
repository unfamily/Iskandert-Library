package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.unfamily.iskalib.IskaLib;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable one-time backup gate before world load (singleplayer world list), like vanilla {@code BackupConfirmScreen}.
 * Host mods call {@link #install(IEventBus, WorldBackupGateConfig)} from common setup.
 */
public final class WorldBackupGate {
    private static final Map<String, WorldBackupGateConfig> CONFIGS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private WorldBackupGate() {
    }

    public static void install(IEventBus modEventBus, WorldBackupGateConfig config) {
        CONFIGS.put(config.registryKey(), config);
        if (!initialized) {
            NeoForge.EVENT_BUS.register(WorldBackupGateServerHooks.class);
            initialized = true;
            IskaLib.LOGGER.info("World backup gate framework enabled (pre-world-load)");
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

    /**
     * First registered gate that still needs a prompt for this world folder, or null.
     */
    public static WorldBackupGateConfig findPendingConfig(Path worldDataDir) {
        for (WorldBackupGateConfig config : CONFIGS.values()) {
            if (WorldBackupGateStorage.requiresBackupPrompt(worldDataDir, config)) {
                return config;
            }
        }
        return null;
    }

    public static WorldBackupGateConfig findPendingConfig(LevelStorageSource.LevelStorageAccess access) {
        return findPendingConfig(WorldBackupGateStorage.worldDataDir(access));
    }

    public static String currentHostModVersion(WorldBackupGateConfig config) {
        return ModList.get()
                .getModContainerById(config.hostModId())
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(config.migrationVersionLabel());
    }

    public static void acknowledgeOnDisk(Path worldDataDir, WorldBackupGateConfig config) {
        WorldBackupGateStorage.acknowledgeOnDisk(worldDataDir, config, currentHostModVersion(config));
    }

    public static void acknowledgeOnDisk(LevelStorageSource.LevelStorageAccess access, WorldBackupGateConfig config) {
        acknowledgeOnDisk(WorldBackupGateStorage.worldDataDir(access), config);
    }

    public static boolean hasLegacyWorldData(MinecraftServer server, WorldBackupGateConfig config) {
        return WorldBackupGateStorage.hasLegacyWorldData(
                server.getWorldPath(LevelResource.ROOT).resolve("data"), config);
    }

    public static boolean isAcknowledged(ServerLevel level, WorldBackupGateConfig config) {
        Path dataDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        if (WorldBackupGateStorage.isAcknowledged(dataDir, config)) {
            return true;
        }
        return WorldBackupGateMetadata.get(level).isAcknowledged(config.registryKey());
    }

    public static void syncAckFromDisk(ServerLevel level, WorldBackupGateConfig config) {
        Path dataDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        if (WorldBackupGateStorage.isAcknowledged(dataDir, config)
                && !WorldBackupGateMetadata.get(level).isAcknowledged(config.registryKey())) {
            WorldBackupGateMetadata.get(level).acknowledge(config.registryKey(), currentHostModVersion(config));
        }
    }

    public static void autoAcknowledgeFreshWorlds(MinecraftServer server, WorldBackupGateConfig config) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        if (WorldBackupGateStorage.isAcknowledged(dataDir, config)) {
            syncAckFromDisk(overworld, config);
            return;
        }
        if (!WorldBackupGateStorage.hasLegacyWorldData(dataDir, config)) {
            WorldBackupGateMetadata.get(overworld).acknowledge(config.registryKey(), currentHostModVersion(config));
            WorldBackupGateStorage.acknowledgeOnDisk(dataDir, config, currentHostModVersion(config));
        }
    }
}
