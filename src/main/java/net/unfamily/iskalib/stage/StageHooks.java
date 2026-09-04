package net.unfamily.iskalib.stage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Integration points for consumers that want side effects on stage changes.
 */
public final class StageHooks {
    private StageHooks() {}

    public interface Listener {
        void onPlayerStageChanged(ServerPlayer player, String stage, boolean value);
        void onWorldStageChanged(MinecraftServer server, String stage, boolean value);
        void onTeamStageChanged(MinecraftServer server, String teamName, String stage, boolean value);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Listener COMPOSITE = new Listener() {
        @Override
        public void onPlayerStageChanged(ServerPlayer player, String stage, boolean value) {
            for (Listener listener : LISTENERS) {
                listener.onPlayerStageChanged(player, stage, value);
            }
        }

        @Override
        public void onWorldStageChanged(MinecraftServer server, String stage, boolean value) {
            for (Listener listener : LISTENERS) {
                listener.onWorldStageChanged(server, stage, value);
            }
        }

        @Override
        public void onTeamStageChanged(MinecraftServer server, String teamName, String stage, boolean value) {
            for (Listener listener : LISTENERS) {
                listener.onTeamStageChanged(server, teamName, stage, value);
            }
        }
    };

    public static Listener getListener() {
        return COMPOSITE;
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void setListener(Listener newListener) {
        LISTENERS.clear();
        addListener(newListener);
    }
}

