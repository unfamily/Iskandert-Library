package net.unfamily.iskalib.stage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

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
    private static final ThreadLocal<ResourceManager> CURRENT_RM = new ThreadLocal<>();

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    /** ResourceManager active while a datapack reload triggers the stage chain, if any. */
    @Nullable
    public static ResourceManager currentResourceManagerOrNull() {
        return CURRENT_RM.get();
    }

    public static void runWithResourceManager(@Nullable ResourceManager rm, Runnable action) {
        ResourceManager previous = CURRENT_RM.get();
        CURRENT_RM.set(rm);
        try {
            action.run();
        } finally {
            if (previous != null) {
                CURRENT_RM.set(previous);
            } else {
                CURRENT_RM.remove();
            }
        }
    }
}
