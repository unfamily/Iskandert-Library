package net.unfamily.iskalib.integration.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.unfamily.iskalib.IskaLibConfig;
import net.unfamily.iskalib.team.ShopTeamManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Syncs FTB Teams membership into shop teams when {@link IskaLibConfig#FTB_TEAMS_SYNC_ENABLED} is on.
 *
 * <p>Loaded reflectively only when FTB Teams is present (see {@link net.unfamily.iskalib.IskaLib}).
 */
public final class FtbTeamsEvents {
    private static final String MOD_ID = "ftbteams";
    private static boolean initialized = false;

    private FtbTeamsEvents() {}

    public static void init() {
        if (initialized) {
            return;
        }
        if (!ModList.get().isLoaded(MOD_ID)
                || !IskaLibConfig.FTB_TEAMS_SYNC_ENABLED.get()
                || !FtbTeamsBridge.isAvailable()) {
            return;
        }
        initialized = true;
        NeoForge.EVENT_BUS.register(FtbTeamsEvents.class);
    }

    @SubscribeEvent
    public static void onTeamCreated(FTBTeamsEvent.TeamCreated event) {
        applyFromTeam(event.getEventData().team(), event.getEventData().creator());
    }

    @SubscribeEvent
    public static void onOwnershipTransferred(FTBTeamsEvent.PlayerTransferredOwnership event) {
        ServerPlayer from = event.getEventData().fromPlayer();
        ServerPlayer to = event.getEventData().toPlayer();
        ServerPlayer hint = to != null ? to : from;
        applyFromTeam(event.getEventData().team(), hint);
    }

    @SubscribeEvent
    public static void onPlayerChangedTeam(FTBTeamsEvent.PlayerChangedTeam event) {
        var data = event.getEventData();
        MinecraftServer server = resolveServer(data.team(), data.player());
        if (server == null || server.overworld() == null) {
            return;
        }
        if (!applyFromTeam(data.team(), data.player())) {
            return;
        }
        UUID teamId = data.team().getTeamId();
        if (teamId != null && data.playerId() != null) {
            ShopTeamManager.getInstance(server.overworld()).setPlayerTeamMapping(data.playerId(), teamId.toString());
        }
    }

    @SubscribeEvent
    public static void onTeamPropertiesChanged(FTBTeamsEvent.TeamPropertiesChanged event) {
        if (event.getEventData().isClient()) {
            return;
        }
        applyFromTeam(event.getEventData().team(), null);
    }

    private static boolean applyFromTeam(Team team, ServerPlayer hintPlayer) {
        if (team == null) {
            return false;
        }
        UUID teamId = team.getTeamId();
        UUID owner = team.getOwner();
        if (teamId == null || owner == null || Util.NIL_UUID.equals(owner)) {
            return false;
        }

        String displayName = team.getProperty(TeamProperties.DISPLAY_NAME);
        if (displayName == null || displayName.isBlank()) {
            displayName = team.getName().getString();
        }

        MinecraftServer server = resolveServer(team, hintPlayer);
        if (server == null || server.overworld() == null) {
            return false;
        }

        Map<UUID, TeamRank> ranks = team.getPlayersByRank(TeamRank.NONE);
        Set<UUID> members = new HashSet<>();
        Set<UUID> assistants = new HashSet<>();
        for (var e : ranks.entrySet()) {
            UUID id = e.getKey();
            TeamRank rank = e.getValue();
            if (id == null || rank == null) {
                continue;
            }
            if (rank.isMemberOrBetter()) {
                members.add(id);
            }
            if (rank.isOfficerOrBetter() && !rank.isOwner()) {
                assistants.add(id);
            }
        }

        ShopTeamManager.getInstance(server.overworld())
                .applyExternalTeamSnapshot(teamId.toString(), displayName, owner, members, assistants);
        return true;
    }

    private static MinecraftServer resolveServer(Team team, ServerPlayer hintPlayer) {
        try {
            if (FTBTeamsAPI.api() != null && FTBTeamsAPI.api().isManagerLoaded()) {
                MinecraftServer fromManager = FTBTeamsAPI.api().getManager().getServer();
                if (fromManager != null) {
                    return fromManager;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return hintPlayer != null ? hintPlayer.level().getServer() : null;
    }
}
