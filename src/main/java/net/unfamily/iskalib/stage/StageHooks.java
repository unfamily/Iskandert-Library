package net.unfamily.iskalib.stage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    private static volatile Listener listener = new Listener() {
        @Override
        public void onPlayerStageChanged(ServerPlayer player, String stage, boolean value) {}

        @Override
        public void onWorldStageChanged(MinecraftServer server, String stage, boolean value) {}

        @Override
        public void onTeamStageChanged(MinecraftServer server, String teamName, String stage, boolean value) {}
    };

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener != null ? newListener : listener;
    }
}

