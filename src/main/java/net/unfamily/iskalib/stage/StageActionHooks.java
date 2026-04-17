package net.unfamily.iskalib.stage;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;

/**
 * Integration points for stage actions exposed through commands.
 *
 * <p>The library provides the command surface; consumers (mods) provide the actual action implementation.
 */
public final class StageActionHooks {
    private StageActionHooks() {}

    public interface Listener {
        /**
         * @return available action ids for suggestions
         */
        List<String> listActionIds();

        /**
         * Executes an action for a list of players.
         *
         * @return -1 if action id not found, 0 if not executed (e.g. onCall=false and force=false),
         * otherwise the number of players affected/executed.
         */
        int executeActionById(String actionId, List<ServerPlayer> players, boolean force);
    }

    private static volatile Listener listener = new Listener() {
        @Override
        public List<String> listActionIds() {
            return Collections.emptyList();
        }

        @Override
        public int executeActionById(String actionId, List<ServerPlayer> players, boolean force) {
            return -1;
        }
    };

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener != null ? newListener : listener;
    }
}

