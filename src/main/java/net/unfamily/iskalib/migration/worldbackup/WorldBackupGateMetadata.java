package net.unfamily.iskalib.migration.worldbackup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-world acknowledgements for {@link WorldBackupGate} instances (keyed by host mod + gate id).
 */
public class WorldBackupGateMetadata extends SavedData {
    private static final String DATA_ID = "iska_lib_world_backup_gates";
    private static final SavedDataType<WorldBackupGateMetadata> DATA_TYPE = new SavedDataType<>(
            Identifier.parse(DATA_ID),
            ignored -> new WorldBackupGateMetadata(),
            ignored -> CompoundTag.CODEC.xmap(WorldBackupGateMetadata::fromTag, WorldBackupGateMetadata::toTag)
    );

    private final Map<String, GateAck> acknowledgements = new HashMap<>();

    public boolean isAcknowledged(String registryKey) {
        return acknowledgements.containsKey(registryKey);
    }

    public void acknowledge(String registryKey, String modVersion) {
        acknowledgements.put(registryKey, new GateAck(modVersion));
        setDirty();
    }

    public static WorldBackupGateMetadata get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DATA_TYPE);
    }

    static WorldBackupGateMetadata fromTag(CompoundTag tag) {
        WorldBackupGateMetadata data = new WorldBackupGateMetadata();
        if (tag.contains("gates")) {
            CompoundTag gates = tag.getCompoundOrEmpty("gates");
            for (String key : gates.keySet()) {
                CompoundTag entry = gates.getCompoundOrEmpty(key);
                entry.getString("version").ifPresent(version -> data.acknowledgements.put(key, new GateAck(version)));
            }
        }
        return data;
    }

    static CompoundTag toTag(WorldBackupGateMetadata data) {
        CompoundTag tag = new CompoundTag();
        CompoundTag gates = new CompoundTag();
        for (Map.Entry<String, GateAck> entry : data.acknowledgements.entrySet()) {
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
