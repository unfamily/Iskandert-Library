package net.unfamily.iskalib.liquid;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.SoundAction;
import net.neoforged.neoforge.common.SoundActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional per-fluid sound map for {@link FluidType.Properties}.
 */
public record LiquidSoundSet(Map<SoundAction, SoundEvent> sounds) {
    public static final LiquidSoundSet DEFAULT = LiquidSoundSet.of(
            SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL,
            SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);

    public LiquidSoundSet {
        sounds = sounds == null || sounds.isEmpty()
                ? DEFAULT.sounds
                : Collections.unmodifiableMap(new LinkedHashMap<>(sounds));
    }

    public static LiquidSoundSet of(SoundAction action, SoundEvent sound) {
        Map<SoundAction, SoundEvent> map = new LinkedHashMap<>();
        map.put(action, sound);
        return new LiquidSoundSet(map);
    }

    public static LiquidSoundSet of(SoundAction a1, SoundEvent s1, SoundAction a2, SoundEvent s2) {
        Map<SoundAction, SoundEvent> map = new LinkedHashMap<>();
        map.put(a1, s1);
        map.put(a2, s2);
        return new LiquidSoundSet(map);
    }

    public LiquidSoundSet with(SoundAction action, SoundEvent sound) {
        Map<SoundAction, SoundEvent> map = new LinkedHashMap<>(sounds);
        map.put(action, sound);
        return new LiquidSoundSet(map);
    }
}
