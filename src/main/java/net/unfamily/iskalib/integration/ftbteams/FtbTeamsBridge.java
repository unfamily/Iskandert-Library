package net.unfamily.iskalib.integration.ftbteams;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Optional FTB Teams bridge.
 *
 * <p>This class must not have any hard references to FTB Teams types, so it can be loaded when FTB Teams is absent.
 */
public final class FtbTeamsBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String FTB_MOD_ID = "ftbteams";

    private static final String CLS_API = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private static final String CLS_TEAM_PROPERTIES = "dev.ftb.mods.ftbteams.api.property.TeamProperties";
    private static final String CLS_TEAM_RANK = "dev.ftb.mods.ftbteams.api.TeamRank";
    private static final String CLS_PARTY_TEAM = "dev.ftb.mods.ftbteams.data.PartyTeam";
    private static final String CLS_NAME_AND_ID = "net.minecraft.server.players.NameAndId";

    private static final String FIELD_DISPLAY_NAME = "DISPLAY_NAME";

    private FtbTeamsBridge() {}

    public static boolean isAvailable() {
        try {
            if (!ModList.get().isLoaded(FTB_MOD_ID)) {
                return false;
            }
            Class.forName(CLS_API, false, FtbTeamsBridge.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Optional<FtbTeamInfo> getEffectiveTeamInfo(ServerPlayer player) {
        if (player == null || !isAvailable()) {
            return Optional.empty();
        }
        try {
            Object team = getTeamForPlayer(player).orElse(null);
            if (team == null) {
                return Optional.empty();
            }
            UUID teamId = (UUID) team.getClass().getMethod("getTeamId").invoke(team);
            UUID ownerId = (UUID) team.getClass().getMethod("getOwner").invoke(team);

            // FTB can transiently return NIL_UUID; if player is owner, bootstrap ownerId with player UUID.
            if (Util.NIL_UUID.equals(ownerId)) {
                Object rank = team.getClass().getMethod("getRankForPlayer", UUID.class).invoke(team, player.getUUID());
                if (rank != null && "OWNER".equals(rank.toString())) {
                    ownerId = player.getUUID();
                }
            }

            String displayName = getDisplayName(team);
            return Optional.of(new FtbTeamInfo(teamId.toString(), displayName, ownerId));
        } catch (Throwable t) {
            LOGGER.debug("FTB Teams bridge getEffectiveTeamInfo failed: {}", t.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<FtbTeamSnapshot> getEffectiveTeamSnapshot(ServerPlayer player) {
        if (player == null || !isAvailable()) {
            return Optional.empty();
        }
        try {
            Object team = getTeamForPlayer(player).orElse(null);
            if (team == null) {
                return Optional.empty();
            }
            return buildSnapshot(team, player.getUUID());
        } catch (Throwable t) {
            LOGGER.debug("FTB Teams bridge getEffectiveTeamSnapshot failed: {}", t.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<FtbTeamSnapshot> getSnapshotByTeamKey(String teamKey) {
        if (teamKey == null || teamKey.isBlank() || !isAvailable()) {
            return Optional.empty();
        }
        try {
            UUID teamId = UUID.fromString(teamKey);
            Object manager = getManager();
            Method getTeamById = manager.getClass().getMethod("getTeamByID", UUID.class);
            Object optTeam = getTeamById.invoke(manager, teamId);
            Object team = unwrapOptional(optTeam);
            if (team == null) {
                return Optional.empty();
            }
            return buildSnapshot(team, null);
        } catch (Throwable t) {
            LOGGER.debug("FTB Teams bridge getSnapshotByTeamKey failed: {}", t.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Promote/demote a party member between MEMBER and OFFICER (assistant) ranks.
     *
     * <p>FTB Teams 26.1.x does not expose promote/demote as a command; GUI uses an internal operation packet which
     * calls {@code PartyTeam.promote/demote}. We replicate that behavior via reflection.
     *
     * @param actor server-side command executor (must be in a party team)
     * @param target player UUID to update
     * @param makeAssistant true => promote to OFFICER, false => demote to MEMBER
     */
    public static boolean setAssistant(ServerPlayer actor, UUID target, boolean makeAssistant) {
        if (actor == null || target == null || !isAvailable()) {
            return false;
        }
        try {
            Object team = getTeamForPlayer(actor).orElse(null);
            if (team == null) {
                return false;
            }

            Class<?> partyTeamClass = Class.forName(CLS_PARTY_TEAM);
            if (!partyTeamClass.isInstance(team)) {
                return false;
            }

            // senderRank / targetRank
            Object senderRank = team.getClass().getMethod("getRankForPlayer", UUID.class).invoke(team, actor.getUUID());
            Object targetRank = team.getClass().getMethod("getRankForPlayer", UUID.class).invoke(team, target);
            if (senderRank == null || targetRank == null) {
                return false;
            }

            // require senderRank >= OWNER
            if (!rankIsAtLeast(senderRank, "OWNER")) {
                return false;
            }

            // Build List<NameAndId> like GUI message does: new NameAndId(targetId, "")
            Class<?> nameAndIdClass = Class.forName(CLS_NAME_AND_ID);
            Object nameAndId = nameAndIdClass.getConstructor(UUID.class, String.class).newInstance(target, "");
            List<?> targetProfile = List.of(nameAndId);

            if (makeAssistant) {
                // promote: targetRank >= MEMBER
                if (!rankIsAtLeast(targetRank, "MEMBER")) {
                    return false;
                }
                Method promote = partyTeamClass.getMethod("promote", ServerPlayer.class, java.util.Collection.class);
                promote.invoke(team, actor, targetProfile);
                return true;
            } else {
                // demote: targetRank >= OFFICER
                if (!rankIsAtLeast(targetRank, "OFFICER")) {
                    return false;
                }
                Method demote = partyTeamClass.getMethod("demote", ServerPlayer.class, java.util.Collection.class);
                demote.invoke(team, actor, targetProfile);
                return true;
            }
        } catch (Throwable t) {
            LOGGER.debug("FTB Teams bridge setAssistant failed: {}", t.getMessage());
            return false;
        }
    }

    private static boolean rankIsAtLeast(Object rankEnum, String minRankName) {
        try {
            Class<?> teamRankClass = Class.forName(CLS_TEAM_RANK);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object minRank = Enum.valueOf((Class<? extends Enum>) teamRankClass, minRankName);
            Method isAtLeast = rankEnum.getClass().getMethod("isAtLeast", teamRankClass);
            Object res = isAtLeast.invoke(rankEnum, minRank);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {
            // fallback to ordinal/compareTo semantics
            return rankEnum != null && minRankName.equals(rankEnum.toString());
        }
    }

    private static Optional<FtbTeamSnapshot> buildSnapshot(Object team, UUID ownerBootstrapCandidate) throws Exception {
        UUID teamId = (UUID) team.getClass().getMethod("getTeamId").invoke(team);
        UUID ownerId = (UUID) team.getClass().getMethod("getOwner").invoke(team);

        if (Util.NIL_UUID.equals(ownerId) && ownerBootstrapCandidate != null) {
            Object rank = team.getClass().getMethod("getRankForPlayer", UUID.class).invoke(team, ownerBootstrapCandidate);
            if (rank != null && "OWNER".equals(rank.toString())) {
                ownerId = ownerBootstrapCandidate;
            }
        }

        String displayName = getDisplayName(team);

        // Build membership and assistants from ranks map.
        Set<UUID> members = new HashSet<>();
        Set<UUID> assistants = new HashSet<>();

        Class<?> teamRankClass = Class.forName(CLS_TEAM_RANK);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object noneRank = Enum.valueOf((Class<? extends Enum>) teamRankClass, "NONE");

        Method getPlayersByRank = team.getClass().getMethod("getPlayersByRank", teamRankClass);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> ranks = (Map<UUID, Object>) getPlayersByRank.invoke(team, noneRank);

        for (Map.Entry<UUID, Object> e : ranks.entrySet()) {
            UUID playerId = e.getKey();
            Object rank = e.getValue();
            if (rank == null) continue;
            String rankName = rank.toString();
            if ("OFFICER".equals(rankName)) {
                assistants.add(playerId);
                members.add(playerId);
            } else if ("MEMBER".equals(rankName) || "OWNER".equals(rankName)) {
                members.add(playerId);
            }
        }

        if (!Util.NIL_UUID.equals(ownerId)) {
            members.add(ownerId);
        }

        return Optional.of(new FtbTeamSnapshot(teamId.toString(), displayName, ownerId, members, assistants));
    }

    private static Optional<Object> getTeamForPlayer(ServerPlayer player) throws Exception {
        Object manager = getManager();
        Method getTeamForPlayer = manager.getClass().getMethod("getTeamForPlayer", ServerPlayer.class);
        Object optTeam = getTeamForPlayer.invoke(manager, player);
        Object team = unwrapOptional(optTeam);
        return Optional.ofNullable(team);
    }

    private static Object getManager() throws Exception {
        Class<?> apiClass = Class.forName(CLS_API);
        Method apiMethod = apiClass.getMethod("api");
        Object api = apiMethod.invoke(null);
        Method getManager = api.getClass().getMethod("getManager");
        return getManager.invoke(api);
    }

    private static String getDisplayName(Object team) {
        try {
            // team.getProperty(TeamProperties.DISPLAY_NAME)
            Class<?> props = Class.forName(CLS_TEAM_PROPERTIES);
            Field displayNameField = props.getField(FIELD_DISPLAY_NAME);
            Object displayNameProp = displayNameField.get(null);
            Class<?> teamPropertyInterface = null;
            for (Class<?> itf : displayNameProp.getClass().getInterfaces()) {
                if ("dev.ftb.mods.ftbteams.api.property.TeamProperty".equals(itf.getName())) {
                    teamPropertyInterface = itf;
                    break;
                }
            }
            if (teamPropertyInterface != null) {
                Method getProperty = team.getClass().getMethod("getProperty", teamPropertyInterface);
                Object value = getProperty.invoke(team, displayNameProp);
                if (value != null) {
                    String s = value.toString();
                    if (!s.isBlank()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
            // fallback below
        }

        try {
            Object nameComponent = team.getClass().getMethod("getName").invoke(team);
            Method getString = nameComponent.getClass().getMethod("getString");
            Object s = getString.invoke(nameComponent);
            return s != null ? s.toString() : team.toString();
        } catch (Throwable t) {
            return team.toString();
        }
    }

    private static Object unwrapOptional(Object opt) {
        if (opt == null) return null;
        if (opt instanceof Optional<?> o) {
            return o.orElse(null);
        }
        try {
            Method isPresent = opt.getClass().getMethod("isPresent");
            boolean present = (boolean) isPresent.invoke(opt);
            if (!present) return null;
            Method get = opt.getClass().getMethod("get");
            return get.invoke(opt);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

