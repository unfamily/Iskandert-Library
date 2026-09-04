package net.unfamily.iskalib.reload;

import net.minecraft.commands.CommandSourceStack;

/**
 * Optional bridge used by consumers that own datapack-backed content.
 */
public final class UtilsReloadHooks {
    private UtilsReloadHooks() {}

    public interface Listener {
        int reloadFromDatapacks(CommandSourceStack source);
    }

    private static volatile Listener listener;

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }
}
