package net.unfamily.iskalib.stage;

import net.minecraft.commands.CommandSourceStack;

/**
 * Optional bridge to reload only the stage content block (actions, items, catalog contributors).
 * Full datapack reload remains on {@link net.unfamily.iskalib.reload.UtilsReloadHooks}.
 */
public final class StageReloadHooks {
    private StageReloadHooks() {}

    public interface Listener {
        /**
         * Reloads stage actions, stage items, and refreshes catalog-backed data.
         *
         * @return number of logical units reloaded (or 1 on success)
         */
        int reloadStageBlock(CommandSourceStack source);
    }

    private static volatile Listener listener;

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }
}
