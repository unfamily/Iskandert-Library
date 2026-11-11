package net.unfamily.iskalib.stage;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Optional bridge so {@code /iska_lib_stage edit} opens Another Stages' Stage Generator GUI.
 * Library registers the {@code edit} literal only when a listener is present (AS installed).
 */
public final class StageEditHooks {
    private StageEditHooks() {}

    @FunctionalInterface
    public interface Listener {
        /** Open the Stage Generator for {@code player} (typically S2C packet from Another Stages). */
        void openEditor(ServerPlayer player);
    }

    private static volatile @Nullable Listener listener;

    @Nullable
    public static Listener getListener() {
        return listener;
    }

    public static void setListener(@Nullable Listener newListener) {
        listener = newListener;
    }
}
