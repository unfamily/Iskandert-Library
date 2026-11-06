package net.unfamily.iskalib.nbt;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only UUID helpers that accept both string and binary IntArray NBT forms.
 * Does not mutate tags or migrate stored formats.
 */
public final class NbtUuid {
    private NbtUuid() {}

    /**
     * Reads a UUID from {@code tag} under {@code key}.
     * Accepts a string UUID or a length-4 IntArray (Minecraft binary UUID).
     * Missing or invalid values return {@code null} without throwing.
     */
    @Nullable
    public static UUID read(@Nullable CompoundTag tag, @Nullable String key) {
        if (tag == null || key == null || !tag.contains(key)) {
            return null;
        }
        var asString = tag.getString(key);
        if (asString.isPresent()) {
            return parseString(asString.get());
        }
        var asInts = tag.getIntArray(key);
        if (asInts.isPresent()) {
            return fromIntArray(asInts.get());
        }
        return null;
    }

    /**
     * Reads a UUID from {@code input} under {@code key}.
     * Accepts a string UUID or a length-4 IntArray (Minecraft binary UUID).
     * Missing or invalid values return {@code null} without throwing.
     */
    @Nullable
    public static UUID read(@Nullable ValueInput input, @Nullable String key) {
        if (input == null || key == null) {
            return null;
        }
        var asString = input.getString(key);
        if (asString.isPresent()) {
            return parseString(asString.get());
        }
        var asInts = input.getIntArray(key);
        if (asInts.isPresent()) {
            return fromIntArray(asInts.get());
        }
        return null;
    }

    @Nullable
    private static UUID parseString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static UUID fromIntArray(int[] ints) {
        if (ints == null || ints.length != 4) {
            return null;
        }
        return UUIDUtil.uuidFromIntArray(ints);
    }
}
