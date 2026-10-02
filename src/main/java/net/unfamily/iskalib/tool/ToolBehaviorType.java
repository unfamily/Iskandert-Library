package net.unfamily.iskalib.tool;

import java.util.Locale;
import java.util.Optional;

public enum ToolBehaviorType {
    LUMBERJACK,
    EXCAVATOR,
    SCYTHE,
    PAXEL;

    public static Optional<ToolBehaviorType> fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "lumberjack" -> Optional.of(LUMBERJACK);
            case "excavator" -> Optional.of(EXCAVATOR);
            case "scythe" -> Optional.of(SCYTHE);
            case "paxel" -> Optional.of(PAXEL);
            default -> Optional.empty();
        };
    }
}
