package net.unfamily.iskalib.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class NbtUuidTest {
    private static final String KEY = "ownerPlayer";
    private static final UUID SAMPLE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void readsValidString() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, SAMPLE.toString());
        assertEquals(SAMPLE, NbtUuid.read(tag, KEY));
    }

    @Test
    void readsValidIntArray() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(KEY, UUIDUtil.uuidToIntArray(SAMPLE));
        assertEquals(SAMPLE, NbtUuid.read(tag, KEY));
    }

    @Test
    void stringAndIntArrayYieldSameUuid() {
        CompoundTag asString = new CompoundTag();
        asString.putString(KEY, SAMPLE.toString());
        CompoundTag asBinary = new CompoundTag();
        asBinary.putIntArray(KEY, UUIDUtil.uuidToIntArray(SAMPLE));
        assertEquals(NbtUuid.read(asString, KEY), NbtUuid.read(asBinary, KEY));
    }

    @Test
    void missingKeyReturnsNull() {
        assertNull(NbtUuid.read(new CompoundTag(), KEY));
        assertNull(NbtUuid.read((CompoundTag) null, KEY));
        assertNull(NbtUuid.read(new CompoundTag(), null));
    }

    @Test
    void malformedStringReturnsNull() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, "not-a-uuid");
        assertNull(NbtUuid.read(tag, KEY));
    }

    @Test
    void invalidIntArrayReturnsNull() {
        CompoundTag shortArr = new CompoundTag();
        shortArr.putIntArray(KEY, new int[] {1, 2, 3});
        assertNull(NbtUuid.read(shortArr, KEY));

        CompoundTag longArr = new CompoundTag();
        longArr.putIntArray(KEY, new int[] {1, 2, 3, 4, 5});
        assertNull(NbtUuid.read(longArr, KEY));
    }

    @Test
    void readDoesNotMutateTag() {
        CompoundTag stringTag = new CompoundTag();
        stringTag.putString(KEY, SAMPLE.toString());
        CompoundTag stringBefore = stringTag.copy();
        assertNotNull(NbtUuid.read(stringTag, KEY));
        assertTrue(stringTag.equals(stringBefore));

        CompoundTag binaryTag = new CompoundTag();
        binaryTag.putIntArray(KEY, UUIDUtil.uuidToIntArray(SAMPLE));
        CompoundTag binaryBefore = binaryTag.copy();
        assertNotNull(NbtUuid.read(binaryTag, KEY));
        assertTrue(binaryTag.equals(binaryBefore));
    }
}
