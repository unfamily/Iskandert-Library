package net.unfamily.iskalib.load;

/**
 * Logic for combining stage or mod gate conditions.
 */
public enum StagesLogic {
    AND,
    OR,
    DEF_AND,
    DEF_OR;

    public static StagesLogic parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AND;
        }
        return switch (raw.toUpperCase()) {
            case "OR" -> OR;
            case "DEF_AND", "DEF" -> DEF_AND;
            case "DEF_OR" -> DEF_OR;
            default -> AND;
        };
    }

    public boolean isDeferred() {
        return this == DEF_AND || this == DEF_OR;
    }
}
