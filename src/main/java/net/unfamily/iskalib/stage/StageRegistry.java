package net.unfamily.iskalib.stage;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.unfamily.iskalib.team.ShopTeamManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registry for game stages (player, world, team).
 */
public class StageRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PLAYER_STAGES_OBJECTIVE = "iska_player_stage";
    private static final String WORLD_STAGE_DATA_NAME = "iska_utils_world_stages";
    private static final String TEAM_STAGE_DATA_NAME = "iska_utils_team_stages";
    private static final SavedDataType<WorldStageData> WORLD_STAGE_DATA_TYPE = new SavedDataType<>(
        Identifier.parse(WORLD_STAGE_DATA_NAME),
        ignored -> new WorldStageData(),
        ignored -> CompoundTag.CODEC.xmap(WorldStageData::fromTag, WorldStageData::toTag)
    );
    private static final SavedDataType<TeamStageData> TEAM_STAGE_DATA_TYPE = new SavedDataType<>(
        Identifier.parse(TEAM_STAGE_DATA_NAME),
        ignored -> new TeamStageData(),
        ignored -> CompoundTag.CODEC.xmap(TeamStageData::fromTag, TeamStageData::toTag)
    );

    private static StageRegistry INSTANCE;

    private final MinecraftServer server;

    private StageRegistry(MinecraftServer server) {
        this.server = server;
    }

    public static StageRegistry getInstance(MinecraftServer server) {
        if (INSTANCE == null || INSTANCE.server != server) {
            INSTANCE = new StageRegistry(server);
        }
        return INSTANCE;
    }

    private void ensurePlayerObjectiveExists() {
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getObjective(PLAYER_STAGES_OBJECTIVE) == null) {
            scoreboard.addObjective(
                PLAYER_STAGES_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Player Stages"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            );
            LOGGER.info("Created player stages scoreboard objective: {}", PLAYER_STAGES_OBJECTIVE);
        }
    }

    public boolean hasPlayerStage(ServerPlayer player, String stage) {
        PlayerStageData data = getPlayerStageData(player);
        return data != null && data.hasStage(stage);
    }

    public boolean hasWorldStage(String stage) {
        WorldStageData data = getWorldStageData(server.getLevel(Level.OVERWORLD));
        return data != null && data.hasStage(stage);
    }

    public boolean hasTeamStage(String teamName, String stage) {
        TeamStageData data = getTeamStageData(server.getLevel(Level.OVERWORLD));
        return data != null && data.hasTeamStage(teamName, stage);
    }

    public boolean hasPlayerTeamStage(ServerPlayer player, String stage) {
        ShopTeamManager teamManager = ShopTeamManager.getInstance((ServerLevel) player.level());
        String teamName = teamManager.getPlayerTeam(player);
        if (teamName == null) {
            return false;
        }
        return hasTeamStage(teamName, stage);
    }

    private PlayerStageData getPlayerStageData(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains("iskautils")) {
            persistentData.put("iskautils", new CompoundTag());
        }

        CompoundTag iskaData = persistentData.getCompoundOrEmpty("iskautils");
        if (!iskaData.contains("stages")) {
            iskaData.put("stages", new ListTag());
            persistentData.put("iskautils", iskaData);
        }

        return new PlayerStageData(player);
    }

    private WorldStageData getWorldStageData(ServerLevel level) {
        if (level == null) {
            LOGGER.warn("Overworld not available, can't access world stages");
            return null;
        }

        return level.getDataStorage().computeIfAbsent(WORLD_STAGE_DATA_TYPE);
    }

    public TeamStageData getTeamStageData(ServerLevel level) {
        if (level == null) {
            LOGGER.warn("Overworld not available, can't access team stages");
            return null;
        }

        return level.getDataStorage().computeIfAbsent(TEAM_STAGE_DATA_TYPE);
    }

    public boolean setPlayerStage(ServerPlayer player, String stage, boolean value) {
        return setPlayerStage(player, stage, value, false);
    }

    public boolean setPlayerStage(ServerPlayer player, String stage, boolean value, boolean hideLog) {
        PlayerStageData data = getPlayerStageData(player);
        if (data == null) {
            return false;
        }

        boolean already = data.hasStage(stage);
        if (value == already) {
            return true;
        }

        if (value) {
            data.addStage(stage);
        } else {
            data.removeStage(stage);
        }

        StageHooks.getListener().onPlayerStageChanged(player, stage, value);

        if (!hideLog) {
            LOGGER.info("Set player stage '{}' to {} for player {}", stage, value, player.getName().getString());
        }
        return true;
    }

    public boolean setWorldStage(String stage, boolean value) {
        return setWorldStage(stage, value, false);
    }

    public boolean setWorldStage(String stage, boolean value, boolean hideLog) {
        WorldStageData data = getWorldStageData(server.getLevel(Level.OVERWORLD));
        if (data == null) {
            LOGGER.error("Failed to access world stage data");
            return false;
        }

        boolean already = data.hasStage(stage);
        if (value == already) {
            return true;
        }

        if (value) {
            data.addStage(stage);
        } else {
            data.removeStage(stage);
        }

        data.setDirty();
        StageHooks.getListener().onWorldStageChanged(server, stage, value);

        if (!hideLog) {
            LOGGER.info("Set world stage '{}' to {}", stage, value);
        }
        return true;
    }

    public boolean setTeamStage(String teamName, String stage, boolean value) {
        return setTeamStage(teamName, stage, value, false);
    }

    public boolean setTeamStage(String teamName, String stage, boolean value, boolean hideLog) {
        TeamStageData data = getTeamStageData(server.getLevel(Level.OVERWORLD));
        if (data == null) {
            LOGGER.error("Failed to access team stage data");
            return false;
        }

        boolean already = data.hasTeamStage(teamName, stage);
        if (value == already) {
            return true;
        }

        if (value) {
            data.addTeamStage(teamName, stage);
        } else {
            data.removeTeamStage(teamName, stage);
        }

        data.setDirty();
        StageHooks.getListener().onTeamStageChanged(server, teamName, stage, value);

        if (!hideLog) {
            LOGGER.info("Set team stage '{}' to {} for team '{}'", stage, value, teamName);
        }
        return true;
    }

    public boolean setPlayerTeamStage(ServerPlayer player, String stage, boolean value) {
        ShopTeamManager teamManager = ShopTeamManager.getInstance((ServerLevel) player.level());
        String teamName = teamManager.getPlayerTeam(player);
        if (teamName == null) {
            LOGGER.warn("Player {} is not in a team, cannot set team stage", player.getName().getString());
            return false;
        }
        return setTeamStage(teamName, stage, value);
    }

    public List<String> getPlayerStages(ServerPlayer player) {
        PlayerStageData data = getPlayerStageData(player);
        if (data == null) {
            return Collections.emptyList();
        }
        return data.getStages();
    }

    public List<String> getWorldStages() {
        WorldStageData data = getWorldStageData(server.getLevel(Level.OVERWORLD));
        if (data == null) {
            return Collections.emptyList();
        }
        return data.getStages();
    }

    public List<String> getTeamStages(String teamName) {
        TeamStageData data = getTeamStageData(server.getLevel(Level.OVERWORLD));
        if (data == null) {
            return Collections.emptyList();
        }
        return data.getTeamStages(teamName);
    }

    public List<String> getPlayerTeamStages(ServerPlayer player) {
        ShopTeamManager teamManager = ShopTeamManager.getInstance((ServerLevel) player.level());
        String teamName = teamManager.getPlayerTeam(player);
        if (teamName == null) {
            return Collections.emptyList();
        }
        return getTeamStages(teamName);
    }

    public Set<String> getAllRegisteredStages() {
        Set<String> result = new HashSet<>();
        result.addAll(getWorldStages());

        TeamStageData teamData = getTeamStageData(server.getLevel(Level.OVERWORLD));
        if (teamData != null) {
            result.addAll(teamData.getAllTeamStages());
        }

        return result;
    }

    public static boolean playerHasStage(Entity player, String stage) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).hasPlayerStage(serverPlayer, stage);
    }

    public static boolean worldHasStage(LevelAccessor level, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).hasWorldStage(stage);
    }

    public static boolean teamHasStage(LevelAccessor level, String teamName, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).hasTeamStage(teamName, stage);
    }

    public static boolean playerTeamHasStage(Entity player, String stage) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).hasPlayerTeamStage(serverPlayer, stage);
    }

    public static boolean addPlayerStage(Entity player, String stage) {
        return addPlayerStage(player, stage, false);
    }

    /**
     * @param hideLog when true, skips INFO log line for this change (hooks still run).
     */
    public static boolean addPlayerStage(Entity player, String stage, boolean hideLog) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setPlayerStage(serverPlayer, stage, true, hideLog);
    }

    public static boolean removePlayerStage(Entity player, String stage) {
        return removePlayerStage(player, stage, false);
    }

    /**
     * @param hideLog when true, skips INFO log line for this change (hooks still run).
     */
    public static boolean removePlayerStage(Entity player, String stage, boolean hideLog) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setPlayerStage(serverPlayer, stage, false, hideLog);
    }

    public static boolean addWorldStage(LevelAccessor level, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setWorldStage(stage, true);
    }

    public static boolean removeWorldStage(LevelAccessor level, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setWorldStage(stage, false);
    }

    public static boolean addTeamStage(LevelAccessor level, String teamName, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setTeamStage(teamName, stage, true);
    }

    public static boolean removeTeamStage(LevelAccessor level, String teamName, String stage) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return false;
        }

        return getInstance(server).setTeamStage(teamName, stage, false);
    }

    private static final class PlayerStageData {
        private final ServerPlayer player;

        private PlayerStageData(ServerPlayer player) {
            this.player = player;
        }

        private ListTag getStageList() {
            CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.contains("iskautils")) {
                persistentData.put("iskautils", new CompoundTag());
            }
            CompoundTag iskaData = persistentData.getCompoundOrEmpty("iskautils");
            if (!iskaData.contains("stages")) {
                iskaData.put("stages", new ListTag());
                persistentData.put("iskautils", iskaData);
            }
            return iskaData.getListOrEmpty("stages");
        }

        private void setStageList(ListTag list) {
            CompoundTag persistentData = player.getPersistentData();
            CompoundTag iskaData = persistentData.getCompoundOrEmpty("iskautils");
            iskaData.put("stages", list);
            persistentData.put("iskautils", iskaData);
        }

        public boolean hasStage(String stage) {
            ListTag stageList = getStageList();
            for (int i = 0; i < stageList.size(); i++) {
                if (stageList.getString(i).orElse("").equals(stage)) {
                    return true;
                }
            }
            return false;
        }

        public void addStage(String stage) {
            if (hasStage(stage)) return;
            ListTag stageList = getStageList();
            stageList.add(StringTag.valueOf(stage));
            setStageList(stageList);
        }

        public void removeStage(String stage) {
            ListTag stageList = getStageList();
            for (int i = 0; i < stageList.size(); i++) {
                if (stageList.getString(i).orElse("").equals(stage)) {
                    stageList.remove(i);
                    break;
                }
            }
            setStageList(stageList);
        }

        public List<String> getStages() {
            ListTag stageList = getStageList();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < stageList.size(); i++) {
                stageList.getString(i).ifPresent(result::add);
            }
            return result;
        }
    }

    public static class WorldStageData extends SavedData {
        private final Set<String> stages = new HashSet<>();

        public WorldStageData() {}

        static WorldStageData fromTag(CompoundTag tag) {
            WorldStageData data = new WorldStageData();
            if (tag.contains("stages")) {
                ListTag stagesTag = tag.getListOrEmpty("stages");
                for (int i = 0; i < stagesTag.size(); i++) {
                    stagesTag.getString(i).ifPresent(data.stages::add);
                }
            }
            return data;
        }

        static CompoundTag toTag(WorldStageData data) {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (String stage : data.stages) {
                list.add(StringTag.valueOf(stage));
            }
            tag.put("stages", list);
            return tag;
        }

        public boolean hasStage(String stage) {
            return stages.contains(stage);
        }

        public void addStage(String stage) {
            stages.add(stage);
            setDirty();
        }

        public void removeStage(String stage) {
            stages.remove(stage);
            setDirty();
        }

        public List<String> getStages() {
            return new ArrayList<>(stages);
        }
    }

    public static class TeamStageData extends SavedData {
        private final Map<String, Set<String>> teamStages = new HashMap<>();

        public TeamStageData() {}

        static TeamStageData fromTag(CompoundTag tag) {
            TeamStageData data = new TeamStageData();
            if (tag.contains("teamStages")) {
                CompoundTag teamsTag = tag.getCompoundOrEmpty("teamStages");
                for (String teamName : teamsTag.keySet()) {
                    ListTag stagesTag = teamsTag.getListOrEmpty(teamName);
                    Set<String> stages = new HashSet<>();
                    for (int i = 0; i < stagesTag.size(); i++) {
                        stagesTag.getString(i).ifPresent(stages::add);
                    }
                    data.teamStages.put(teamName, stages);
                }
            }
            return data;
        }

        static CompoundTag toTag(TeamStageData data) {
            CompoundTag tag = new CompoundTag();
            CompoundTag teamsTag = new CompoundTag();
            for (Map.Entry<String, Set<String>> entry : data.teamStages.entrySet()) {
                ListTag list = new ListTag();
                for (String stage : entry.getValue()) {
                    list.add(StringTag.valueOf(stage));
                }
                teamsTag.put(entry.getKey(), list);
            }
            tag.put("teamStages", teamsTag);
            return tag;
        }

        public boolean hasTeamStage(String teamName, String stage) {
            return teamStages.getOrDefault(teamName, Collections.emptySet()).contains(stage);
        }

        public void addTeamStage(String teamName, String stage) {
            teamStages.computeIfAbsent(teamName, k -> new HashSet<>()).add(stage);
            setDirty();
        }

        public void removeTeamStage(String teamName, String stage) {
            Set<String> stages = teamStages.get(teamName);
            if (stages != null) {
                stages.remove(stage);
                if (stages.isEmpty()) {
                    teamStages.remove(teamName);
                }
                setDirty();
            }
        }

        public List<String> getTeamStages(String teamName) {
            return new ArrayList<>(teamStages.getOrDefault(teamName, Collections.emptySet()));
        }

        public Set<String> getAllTeamStages() {
            Set<String> result = new HashSet<>();
            for (Set<String> stages : teamStages.values()) {
                result.addAll(stages);
            }
            return result;
        }

        public Set<String> getAllTeams() {
            return new HashSet<>(teamStages.keySet());
        }
    }
}

