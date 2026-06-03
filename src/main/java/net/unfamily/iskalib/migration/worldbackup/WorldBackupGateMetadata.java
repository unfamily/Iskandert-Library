package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.unfamily.iskalib.IskaLib;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-world acknowledgements for {@link WorldBackupGate} instances (keyed by host mod + gate id).
 */
public class WorldBackupGateMetadata extends SavedData {
    private static final String DATA_NAME = IskaLib.MOD_ID + "_world_backup_gates";

    private final Map<String, GateAck> acknowledgements = new HashMap<>();

    public boolean isAcknowledged(String registryKey) {
        return acknowledgements.containsKey(registryKey);
    }

    public String getRecordedVersion(String registryKey) {
        GateAck ack = acknowledgements.get(registryKey);
        return ack == null ? "" : ack.recordedVersion();
    }

    public void acknowledge(String registryKey, String modVersion) {
        acknowledgements.put(registryKey, new GateAck(modVersion));
        setDirty();
    }

    public static WorldBackupGateMetadata get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(WorldBackupGateMetadata::new, WorldBackupGateMetadata::load),
                DATA_NAME);
    }

    public static WorldBackupGateMetadata load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldBackupGateMetadata data = new WorldBackupGateMetadata();
        CompoundTag gates = tag.getCompound("gates");
        for (String key : gates.getAllKeys()) {
            CompoundTag entry = gates.getCompound(key);
            data.acknowledgements.put(key, new GateAck(entry.getString("version")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag gates = new CompoundTag();
        for (Map.Entry<String, GateAck> entry : acknowledgements.entrySet()) {
            CompoundTag gateTag = new CompoundTag();
            gateTag.putString("version", entry.getValue().recordedVersion());
            gates.put(entry.getKey(), gateTag);
        }
        tag.put("gates", gates);
        return tag;
    }

    private record GateAck(String recordedVersion) {
    }
}
