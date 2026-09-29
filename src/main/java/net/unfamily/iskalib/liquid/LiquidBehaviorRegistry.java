package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side behavior overlays for liquids identified by fluid id.
 * <p>
 * Datapack JSON cannot register new fluid blocks after registries freeze. Overlays update
 * infinity / loggable intent / dimension ticks for already-registered fluids. Full fluid block
 * registration from JSON requires a restart and a mod-init registration path.
 * <p>
 * When {@code infinity} or {@code loggable} is {@code null} on an overlay, callers must keep the
 * registered default (JSON did not declare that key).
 */
public final class LiquidBehaviorRegistry {
    public record Overlay(
            @Nullable Boolean infinity,
            @Nullable Boolean loggable,
            List<DimensionTickTransform> dimensionTicks
    ) {
        public static final Overlay EMPTY = new Overlay(null, null, List.of());

        public Overlay {
            dimensionTicks = dimensionTicks == null ? List.of() : List.copyOf(dimensionTicks);
        }

        public boolean hasDimensionTicks() {
            return !dimensionTicks.isEmpty();
        }
    }

    private static final Map<Identifier, Overlay> BY_FLUID = new ConcurrentHashMap<>();

    private LiquidBehaviorRegistry() {}

    public static void clear() {
        BY_FLUID.clear();
    }

    public static void put(Identifier fluidId, Overlay overlay) {
        if (fluidId == null || overlay == null) {
            return;
        }
        BY_FLUID.put(fluidId, overlay);
    }

    public static void putAll(Map<Identifier, Overlay> overlays) {
        BY_FLUID.clear();
        if (overlays != null) {
            BY_FLUID.putAll(overlays);
        }
    }

    public static Overlay get(Identifier fluidId) {
        if (fluidId == null) {
            return Overlay.EMPTY;
        }
        return BY_FLUID.getOrDefault(fluidId, Overlay.EMPTY);
    }

    @Nullable
    public static Overlay find(Identifier fluidId) {
        return fluidId == null ? null : BY_FLUID.get(fluidId);
    }

    public static Map<Identifier, Overlay> all() {
        return Collections.unmodifiableMap(BY_FLUID);
    }

    public static boolean canConvertToSource(Identifier fluidId, boolean registeredDefault) {
        Overlay overlay = find(fluidId);
        if (overlay != null && overlay.infinity() != null) {
            return overlay.infinity();
        }
        return registeredDefault;
    }

    public static boolean isLoggable(Identifier fluidId, boolean registeredDefault) {
        Overlay overlay = find(fluidId);
        if (overlay != null && overlay.loggable() != null) {
            return overlay.loggable();
        }
        return registeredDefault;
    }

    public static List<DimensionTickTransform> dimensionTicks(Identifier fluidId, List<DimensionTickTransform> registered) {
        Overlay overlay = find(fluidId);
        if (overlay != null && overlay.hasDimensionTicks()) {
            return overlay.dimensionTicks();
        }
        return registered == null ? List.of() : registered;
    }
}
