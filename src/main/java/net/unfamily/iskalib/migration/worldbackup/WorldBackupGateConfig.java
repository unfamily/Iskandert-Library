package net.unfamily.iskalib.migration.worldbackup;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Host-mod configuration for a one-time world backup confirmation gate before a breaking update.
 */
public final class WorldBackupGateConfig {
    private final String hostModId;
    private final String gateId;
    private final String migrationVersionLabel;
    private final List<String> legacyWorldDataFileNames;
    private final String translationPrefix;
    private final String legacyAckSavedDataName;
    private final String legacyAckNbtKey;

    private WorldBackupGateConfig(
            String hostModId,
            String gateId,
            String migrationVersionLabel,
            List<String> legacyWorldDataFileNames,
            String translationPrefix,
            String legacyAckSavedDataName,
            String legacyAckNbtKey) {
        this.hostModId = hostModId;
        this.gateId = gateId;
        this.migrationVersionLabel = migrationVersionLabel;
        this.legacyWorldDataFileNames = List.copyOf(legacyWorldDataFileNames);
        this.translationPrefix = translationPrefix;
        this.legacyAckSavedDataName = legacyAckSavedDataName;
        this.legacyAckNbtKey = legacyAckNbtKey;
    }

    public static Builder builder(String hostModId) {
        return new Builder(hostModId);
    }

    public String registryKey() {
        return hostModId + "_" + gateId;
    }

    public String hostModId() {
        return hostModId;
    }

    public String gateId() {
        return gateId;
    }

    public String migrationVersionLabel() {
        return migrationVersionLabel;
    }

    public List<String> legacyWorldDataFileNames() {
        return legacyWorldDataFileNames;
    }

    public String translationPrefix() {
        return translationPrefix;
    }

    public String titleKey() {
        return translationPrefix + ".title";
    }

    public String warningKey() {
        return translationPrefix + ".warning";
    }

    public String confirmKey() {
        return translationPrefix + ".confirm";
    }

    public String cancelKey() {
        return translationPrefix + ".cancel";
    }

    public String declinedKey() {
        return translationPrefix + ".declined";
    }

    public String legacyAckSavedDataName() {
        return legacyAckSavedDataName;
    }

    public String legacyAckNbtKey() {
        return legacyAckNbtKey;
    }

    public boolean hasLegacyAckMigration() {
        return legacyAckSavedDataName != null && !legacyAckSavedDataName.isBlank()
                && legacyAckNbtKey != null && !legacyAckNbtKey.isBlank();
    }

    public static final class Builder {
        private final String hostModId;
        private String gateId = "default";
        private String migrationVersionLabel = "";
        private List<String> legacyWorldDataFileNames = List.of();
        private String translationPrefix = "message.iska_lib.world_backup_gate";
        private String legacyAckSavedDataName;
        private String legacyAckNbtKey;

        private Builder(String hostModId) {
            this.hostModId = Objects.requireNonNull(hostModId, "hostModId");
        }

        public Builder gateId(String gateId) {
            this.gateId = gateId;
            return this;
        }

        public Builder migrationVersionLabel(String migrationVersionLabel) {
            this.migrationVersionLabel = migrationVersionLabel;
            return this;
        }

        public Builder legacyWorldDataFileNames(List<String> legacyWorldDataFileNames) {
            this.legacyWorldDataFileNames = legacyWorldDataFileNames;
            return this;
        }

        public Builder legacyWorldDataFileNames(String... fileNames) {
            this.legacyWorldDataFileNames = List.of(fileNames);
            return this;
        }

        public Builder translationPrefix(String translationPrefix) {
            this.translationPrefix = translationPrefix;
            return this;
        }

        public Builder legacyAckSavedData(String savedDataName, String nbtKey) {
            this.legacyAckSavedDataName = savedDataName;
            this.legacyAckNbtKey = nbtKey;
            return this;
        }

        public WorldBackupGateConfig build() {
            if (migrationVersionLabel == null || migrationVersionLabel.isBlank()) {
                throw new IllegalStateException("migrationVersionLabel is required");
            }
            return new WorldBackupGateConfig(
                    hostModId,
                    gateId,
                    migrationVersionLabel,
                    legacyWorldDataFileNames == null ? Collections.emptyList() : legacyWorldDataFileNames,
                    translationPrefix,
                    legacyAckSavedDataName,
                    legacyAckNbtKey);
        }
    }
}
