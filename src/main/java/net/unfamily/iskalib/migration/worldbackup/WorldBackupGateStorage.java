package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.unfamily.iskalib.IskaLib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Per-world backup-gate state under {@code world/data/} (not per-player).
 */
public final class WorldBackupGateStorage {
    private static final String GATES_FILE = IskaLib.MOD_ID + "_world_backup_gates.dat";

    private WorldBackupGateStorage() {
    }

    public static Path worldDataDir(LevelStorageSource.LevelStorageAccess access) {
        return access.getLevelPath(LevelResource.ROOT).resolve("data");
    }

    private static Path ackMarkerPath(Path dataDir, WorldBackupGateConfig config) {
        return dataDir.resolve("iska_lib_backup_ack_" + config.hostModId() + "_" + config.gateId() + ".marker");
    }

    public static boolean hasLegacyWorldData(Path dataDir, WorldBackupGateConfig config) {
        if (config.legacyWorldDataFileNames().isEmpty() || !Files.isDirectory(dataDir)) {
            return false;
        }
        for (String fileName : config.legacyWorldDataFileNames()) {
            if (Files.exists(dataDir.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAcknowledged(Path dataDir, WorldBackupGateConfig config) {
        if (Files.isRegularFile(ackMarkerPath(dataDir, config))) {
            return true;
        }
        if (isAcknowledgedInGateFile(dataDir, config.registryKey())) {
            return true;
        }
        return hasLegacyAckOnDisk(dataDir, config);
    }

    public static boolean requiresBackupPrompt(Path dataDir, WorldBackupGateConfig config) {
        if (!hasLegacyWorldData(dataDir, config)) {
            return false;
        }
        return !isAcknowledged(dataDir, config);
    }

    public static void acknowledgeOnDisk(Path dataDir, WorldBackupGateConfig config, String modVersion) {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(
                    ackMarkerPath(dataDir, config),
                    modVersion,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);
            writeGateFileEntry(dataDir, config, modVersion);
            writeLegacyAck(dataDir, config);
        } catch (IOException e) {
            IskaLib.LOGGER.warn("Failed to write backup gate ack for {}: {}", config.registryKey(), e.getMessage());
        }
    }

    private static void writeGateFileEntry(Path dataDir, WorldBackupGateConfig config, String modVersion) throws IOException {
        CompoundTag root = readGateFile(dataDir);
        CompoundTag gates = root.contains("gates") ? root.getCompoundOrEmpty("gates") : new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("version", modVersion);
        gates.put(config.registryKey(), entry);
        root.put("gates", gates);
        NbtIo.writeCompressed(root, dataDir.resolve(GATES_FILE));
    }

    private static void writeLegacyAck(Path dataDir, WorldBackupGateConfig config) throws IOException {
        if (!config.hasLegacyAckMigration()) {
            return;
        }
        Path path = dataDir.resolve(config.legacyAckSavedDataName() + ".dat");
        CompoundTag root = Files.isRegularFile(path)
                ? NbtIo.readCompressed(path, NbtAccounter.create(1048576L))
                : new CompoundTag();
        root.putBoolean(config.legacyAckNbtKey(), true);
        NbtIo.writeCompressed(root, path);
    }

    private static boolean isAcknowledgedInGateFile(Path dataDir, String registryKey) {
        try {
            CompoundTag root = readGateFile(dataDir);
            return root.contains("gates") && root.getCompoundOrEmpty("gates").contains(registryKey);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasLegacyAckOnDisk(Path dataDir, WorldBackupGateConfig config) {
        if (!config.hasLegacyAckMigration()) {
            return false;
        }
        Path path = dataDir.resolve(config.legacyAckSavedDataName() + ".dat");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(1048576L));
            return root.getBoolean(config.legacyAckNbtKey()).orElse(false);
        } catch (IOException e) {
            IskaLib.LOGGER.warn("Failed to read legacy backup ack from {}: {}", path, e.getMessage());
            return false;
        }
    }

    private static CompoundTag readGateFile(Path dataDir) throws IOException {
        Path path = dataDir.resolve(GATES_FILE);
        if (!Files.isRegularFile(path)) {
            return new CompoundTag();
        }
        return NbtIo.readCompressed(path, NbtAccounter.create(1048576L));
    }
}
