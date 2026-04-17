package net.unfamily.iskalib.team;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages teams for sharing balances between team members.
 */
public class ShopTeamManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEAM_DATA_NAME = "iska_utils_shop_teams";
    private static final SavedDataType<TeamData> TEAM_DATA_TYPE = new SavedDataType<>(
        Identifier.parse(TEAM_DATA_NAME),
        ignored -> new TeamData(),
        ignored -> CompoundTag.CODEC.xmap(TeamData::fromTag, TeamData::toTag)
    );

    private static ShopTeamManager INSTANCE;
    private final ServerLevel level;

    private ShopTeamManager(ServerLevel level) {
        this.level = level;
    }

    public static ShopTeamManager getInstance(ServerLevel level) {
        if (INSTANCE == null || INSTANCE.level != level) {
            INSTANCE = new ShopTeamManager(level);
        }
        return INSTANCE;
    }

    private TeamData getTeamData() {
        return level.getDataStorage().computeIfAbsent(TEAM_DATA_TYPE);
    }

    public TeamData getTeamDataInstance() {
        return level.getDataStorage().computeIfAbsent(TEAM_DATA_TYPE);
    }

    public boolean createTeam(String teamName, ServerPlayer leader) {
        TeamData data = getTeamData();
        return data.createTeam(teamName, leader.getUUID());
    }

    public boolean deleteTeam(String teamName, ServerPlayer player) {
        TeamData data = getTeamData();
        return data.deleteTeam(teamName, player.getUUID());
    }

    public boolean addPlayerToTeam(String teamName, ServerPlayer player) {
        TeamData data = getTeamData();
        return data.addPlayerToTeam(teamName, player.getUUID());
    }

    public boolean removePlayerFromTeam(String teamName, ServerPlayer player) {
        TeamData data = getTeamData();
        return data.removePlayerFromTeam(teamName, player.getUUID());
    }

    public String getPlayerTeam(ServerPlayer player) {
        TeamData data = getTeamData();
        return data.getPlayerTeam(player.getUUID());
    }

    public List<UUID> getTeamMembers(String teamName) {
        TeamData data = getTeamData();
        return data.getTeamMembers(teamName);
    }

    public UUID getTeamLeader(String teamName) {
        TeamData data = getTeamData();
        return data.getTeamLeader(teamName);
    }

    public boolean addTeamCurrency(String teamName, String currencyId, double amount) {
        TeamData data = getTeamData();
        return data.addTeamValutes(teamName, currencyId, amount);
    }

    public boolean addTeamValutes(String teamName, String valuteId, double amount) {
        return addTeamCurrency(teamName, valuteId, amount);
    }

    public boolean removeTeamCurrency(String teamName, String currencyId, double amount) {
        TeamData data = getTeamData();
        return data.removeTeamValutes(teamName, currencyId, amount);
    }

    public boolean removeTeamValutes(String teamName, String valuteId, double amount) {
        return removeTeamCurrency(teamName, valuteId, amount);
    }

    public double getTeamCurrencyBalance(String teamName, String currencyId) {
        TeamData data = getTeamData();
        return data.getTeamValuteBalance(teamName, currencyId);
    }

    public double getTeamValuteBalance(String teamName, String valuteId) {
        return getTeamCurrencyBalance(teamName, valuteId);
    }

    public Set<String> getAllTeams() {
        TeamData data = getTeamData();
        return data.getAllTeams();
    }

    public boolean isPlayerInTeam(ServerPlayer player) {
        return getPlayerTeam(player) != null;
    }

    public boolean isPlayerTeamLeader(ServerPlayer player, String teamName) {
        UUID leader = getTeamLeader(teamName);
        return leader != null && leader.equals(player.getUUID());
    }

    public boolean isPlayerTeamAssistant(ServerPlayer player, String teamName) {
        TeamData data = getTeamData();
        return data.isPlayerTeamAssistant(player.getUUID(), teamName);
    }

    public boolean canModifyTeam(ServerPlayer player, String teamName) {
        return isPlayerTeamLeader(player, teamName) || isPlayerTeamAssistant(player, teamName);
    }

    public boolean invitePlayerToTeam(String teamName, ServerPlayer inviter, ServerPlayer invitee) {
        TeamData data = getTeamData();
        return data.invitePlayerToTeam(teamName, inviter.getUUID(), invitee.getUUID());
    }

    public boolean acceptTeamInvitation(ServerPlayer player, String teamName) {
        TeamData data = getTeamData();
        return data.acceptTeamInvitation(player.getUUID(), teamName);
    }

    public List<String> getPlayerInvitations(ServerPlayer player) {
        TeamData data = getTeamData();
        return data.getPlayerInvitations(player.getUUID());
    }

    public boolean leaveTeam(ServerPlayer player) {
        TeamData data = getTeamData();
        return data.leaveTeam(player.getUUID());
    }

    public boolean transferLeadership(String teamName, ServerPlayer currentLeader, ServerPlayer newLeader) {
        TeamData data = getTeamData();
        return data.transferLeadership(teamName, currentLeader.getUUID(), newLeader.getUUID());
    }

    public boolean addTeamAssistant(String teamName, ServerPlayer leader, ServerPlayer assistant) {
        TeamData data = getTeamData();
        return data.addTeamAssistant(teamName, leader.getUUID(), assistant.getUUID());
    }

    public boolean removeTeamAssistant(String teamName, ServerPlayer leader, ServerPlayer assistant) {
        TeamData data = getTeamData();
        return data.removeTeamAssistant(teamName, leader.getUUID(), assistant.getUUID());
    }

    public List<UUID> getTeamAssistants(String teamName) {
        TeamData data = getTeamData();
        return data.getTeamAssistants(teamName);
    }

    public boolean cancelTeamInvitation(String teamName, ServerPlayer canceller, ServerPlayer invitee) {
        TeamData data = getTeamData();
        return data.cancelTeamInvitation(teamName, canceller.getUUID(), invitee.getUUID());
    }

    public boolean renameTeam(String oldTeamName, String newTeamName, ServerPlayer player) {
        TeamData data = getTeamData();
        return data.renameTeam(oldTeamName, newTeamName, player.getUUID());
    }

    public Team getTeamById(UUID teamId) {
        TeamData data = getTeamData();
        return data.getTeamById(teamId);
    }

    public UUID getTeamIdByName(String teamName) {
        TeamData data = getTeamData();
        return data.getTeamIdByName(teamName);
    }

    public String getTeamNameById(UUID teamId) {
        Team team = getTeamById(teamId);
        return team != null ? team.getName() : null;
    }

    public String getPlayerTeam(UUID playerId) {
        return getTeamData().getPlayerTeam(playerId);
    }

    public List<String> getAllTeamNames() {
        return new ArrayList<>(getAllTeams());
    }

    public static class TeamData extends SavedData {
        private final Map<String, Team> teams = new HashMap<>();
        private final Map<UUID, Team> teamsById = new HashMap<>();
        private final Map<UUID, String> playerTeams = new HashMap<>();
        private final Map<UUID, Map<String, Long>> playerInvitations = new HashMap<>();

        public TeamData() {}

        static TeamData fromTag(CompoundTag tag) {
            TeamData data = new TeamData();

            if (tag.contains("teams")) {
                CompoundTag teamsTag = tag.getCompoundOrEmpty("teams");
                for (String teamName : teamsTag.keySet()) {
                    CompoundTag teamTag = teamsTag.getCompoundOrEmpty(teamName);
                    Team team = Team.load(teamTag);
                    data.teams.put(teamName, team);
                    data.teamsById.put(team.getTeamId(), team);

                    for (UUID playerId : team.getMembers()) {
                        data.playerTeams.put(playerId, teamName);
                    }
                }
            }

            if (tag.contains("invitations")) {
                CompoundTag invitationsTag = tag.getCompoundOrEmpty("invitations");
                for (String playerIdStr : invitationsTag.keySet()) {
                    try {
                        UUID playerId = UUID.fromString(playerIdStr);
                        CompoundTag playerInvitationsTag = invitationsTag.getCompoundOrEmpty(playerIdStr);
                        Map<String, Long> invitations = new HashMap<>();
                        for (String teamName : playerInvitationsTag.keySet()) {
                            invitations.put(teamName, playerInvitationsTag.getLong(teamName).orElse(0L));
                        }
                        data.playerInvitations.put(playerId, invitations);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in invitations: {}", playerIdStr);
                    }
                }
            }

            return data;
        }

        static CompoundTag toTag(TeamData data) {
            CompoundTag tag = new CompoundTag();

            CompoundTag teamsTag = new CompoundTag();
            for (Map.Entry<String, Team> entry : data.teams.entrySet()) {
                teamsTag.put(entry.getKey(), entry.getValue().save());
            }
            tag.put("teams", teamsTag);

            CompoundTag invitationsTag = new CompoundTag();
            for (Map.Entry<UUID, Map<String, Long>> entry : data.playerInvitations.entrySet()) {
                CompoundTag playerInvitationsTag = new CompoundTag();
                for (Map.Entry<String, Long> invitationEntry : entry.getValue().entrySet()) {
                    playerInvitationsTag.putLong(invitationEntry.getKey(), invitationEntry.getValue());
                }
                invitationsTag.put(entry.getKey().toString(), playerInvitationsTag);
            }
            tag.put("invitations", invitationsTag);

            return tag;
        }

        public boolean createTeam(String teamName, UUID leader) {
            if (teams.containsKey(teamName)) return false;
            if (playerTeams.containsKey(leader)) return false;

            Team team = new Team(teamName, leader);
            teams.put(teamName, team);
            teamsById.put(team.getTeamId(), team);
            playerTeams.put(leader, teamName);
            setDirty();
            return true;
        }

        public boolean deleteTeam(String teamName, UUID player) {
            Team team = teams.get(teamName);
            if (team == null || !team.getLeader().equals(player)) return false;

            for (UUID memberId : team.getMembers()) {
                playerTeams.remove(memberId);
            }

            teams.remove(teamName);
            teamsById.remove(team.getTeamId());
            setDirty();
            return true;
        }

        public boolean addPlayerToTeam(String teamName, UUID player) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (playerTeams.containsKey(player)) return false;

            team.addMember(player);
            playerTeams.put(player, teamName);
            setDirty();
            return true;
        }

        public boolean removePlayerFromTeam(String teamName, UUID player) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (team.getLeader().equals(player)) return false;
            if (!team.getMembers().contains(player)) return false;

            team.removeMember(player);
            playerTeams.remove(player);
            setDirty();
            return true;
        }

        public String getPlayerTeam(UUID player) {
            return playerTeams.get(player);
        }

        public List<UUID> getTeamMembers(String teamName) {
            Team team = teams.get(teamName);
            return team != null ? new ArrayList<>(team.getMembers()) : new ArrayList<>();
        }

        public UUID getTeamLeader(String teamName) {
            Team team = teams.get(teamName);
            return team != null ? team.getLeader() : null;
        }

        public boolean addTeamValutes(String teamName, String valuteId, double amount) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            team.addValutes(valuteId, amount);
            setDirty();
            return true;
        }

        public boolean removeTeamValutes(String teamName, String valuteId, double amount) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (team.getValuteBalance(valuteId) < amount) return false;
            team.removeValutes(valuteId, amount);
            setDirty();
            return true;
        }

        public double getTeamValuteBalance(String teamName, String valuteId) {
            Team team = teams.get(teamName);
            return team != null ? team.getValuteBalance(valuteId) : 0.0;
        }

        public Set<String> getAllTeams() {
            return new HashSet<>(teams.keySet());
        }

        public Team getTeamById(UUID teamId) {
            return teamsById.get(teamId);
        }

        public UUID getTeamIdByName(String teamName) {
            Team team = teams.get(teamName);
            return team != null ? team.getTeamId() : null;
        }

        public boolean invitePlayerToTeam(String teamName, UUID inviter, UUID invitee) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (!team.getMembers().contains(inviter)) return false;
            if (playerTeams.containsKey(invitee)) return false;

            playerInvitations.computeIfAbsent(invitee, k -> new HashMap<>()).put(teamName, System.currentTimeMillis());
            setDirty();
            return true;
        }

        public boolean acceptTeamInvitation(UUID player, String teamName) {
            Map<String, Long> invitations = playerInvitations.get(player);
            if (invitations == null || !invitations.containsKey(teamName)) return false;
            if (playerTeams.containsKey(player)) return false;

            Team team = teams.get(teamName);
            if (team == null) return false;

            team.addMember(player);
            playerTeams.put(player, teamName);

            invitations.remove(teamName);
            if (invitations.isEmpty()) playerInvitations.remove(player);
            setDirty();
            return true;
        }

        public List<String> getPlayerInvitations(UUID player) {
            Map<String, Long> invitations = playerInvitations.get(player);
            if (invitations == null) return new ArrayList<>();

            long currentTime = System.currentTimeMillis();
            long expiryTime = 24L * 60 * 60 * 1000;

            List<String> validInvitations = new ArrayList<>();
            for (Map.Entry<String, Long> entry : invitations.entrySet()) {
                if (currentTime - entry.getValue() < expiryTime) {
                    validInvitations.add(entry.getKey());
                }
            }
            return validInvitations;
        }

        public boolean leaveTeam(UUID player) {
            String teamName = playerTeams.get(player);
            if (teamName == null) return false;

            Team team = teams.get(teamName);
            if (team == null) return false;
            if (team.getLeader().equals(player)) return false;

            team.removeMember(player);
            playerTeams.remove(player);
            setDirty();
            return true;
        }

        public boolean transferLeadership(String teamName, UUID currentLeader, UUID newLeader) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (!team.getLeader().equals(currentLeader)) return false;
            if (!team.getMembers().contains(newLeader)) return false;
            if (currentLeader.equals(newLeader)) return false;

            team.setLeader(newLeader);
            setDirty();
            return true;
        }

        public boolean addTeamAssistant(String teamName, UUID leader, UUID assistant) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (!team.getLeader().equals(leader)) return false;
            if (!team.getMembers().contains(assistant)) return false;
            if (team.getLeader().equals(assistant)) return false;

            team.addAssistant(assistant);
            setDirty();
            return true;
        }

        public boolean removeTeamAssistant(String teamName, UUID leader, UUID assistant) {
            Team team = teams.get(teamName);
            if (team == null) return false;
            if (!team.getLeader().equals(leader)) return false;

            team.removeAssistant(assistant);
            setDirty();
            return true;
        }

        public List<UUID> getTeamAssistants(String teamName) {
            Team team = teams.get(teamName);
            return team != null ? new ArrayList<>(team.getAssistants()) : new ArrayList<>();
        }

        public boolean isPlayerTeamAssistant(UUID player, String teamName) {
            Team team = teams.get(teamName);
            return team != null && team.getAssistants().contains(player);
        }

        public boolean cancelTeamInvitation(String teamName, UUID canceller, UUID invitee) {
            Team team = teams.get(teamName);
            if (team == null) return false;

            if (!team.getLeader().equals(canceller) && !team.getAssistants().contains(canceller)) return false;

            Map<String, Long> invitations = playerInvitations.get(invitee);
            if (invitations == null || !invitations.containsKey(teamName)) return false;

            invitations.remove(teamName);
            if (invitations.isEmpty()) playerInvitations.remove(invitee);
            setDirty();
            return true;
        }

        public boolean renameTeam(String oldTeamName, String newTeamName, UUID player) {
            Team team = teams.get(oldTeamName);
            if (team == null) return false;
            if (!team.getLeader().equals(player) && !team.getAssistants().contains(player)) return false;
            if (teams.containsKey(newTeamName)) return false;

            teams.remove(oldTeamName);
            team.setName(newTeamName);
            teams.put(newTeamName, team);

            for (UUID memberId : team.getMembers()) {
                playerTeams.put(memberId, newTeamName);
            }

            setDirty();
            return true;
        }

        public void cleanupExpiredInvitations() {
            long currentTime = System.currentTimeMillis();
            long expiryTime = 24L * 60 * 60 * 1000;

            boolean changed = false;
            Iterator<Map.Entry<UUID, Map<String, Long>>> playerIterator = playerInvitations.entrySet().iterator();
            while (playerIterator.hasNext()) {
                Map.Entry<UUID, Map<String, Long>> playerEntry = playerIterator.next();
                Map<String, Long> invitations = playerEntry.getValue();

                Iterator<Map.Entry<String, Long>> invitationIterator = invitations.entrySet().iterator();
                while (invitationIterator.hasNext()) {
                    Map.Entry<String, Long> invitationEntry = invitationIterator.next();
                    if (currentTime - invitationEntry.getValue() >= expiryTime) {
                        invitationIterator.remove();
                        changed = true;
                    }
                }

                if (invitations.isEmpty()) {
                    playerIterator.remove();
                    changed = true;
                }
            }

            if (changed) setDirty();
        }
    }

    public static class Team {
        private UUID teamId;
        private String name;
        private UUID leader;
        private final Set<UUID> members;
        private final Set<UUID> assistants;
        private final Map<String, Double> valuteBalances;

        public Team(String name, UUID leader) {
            this.teamId = UUID.randomUUID();
            this.name = name;
            this.leader = leader;
            this.members = new HashSet<>();
            this.assistants = new HashSet<>();
            this.valuteBalances = new HashMap<>();
            this.members.add(leader);
        }

        private Team(UUID teamId, String name, UUID leader) {
            this.teamId = teamId;
            this.name = name;
            this.leader = leader;
            this.members = new HashSet<>();
            this.assistants = new HashSet<>();
            this.valuteBalances = new HashMap<>();
            this.members.add(leader);
        }

        public UUID getTeamId() {
            return teamId;
        }

        public String getName() {
            return name;
        }

        public UUID getLeader() {
            return leader;
        }

        public void setLeader(UUID newLeader) {
            this.leader = newLeader;
        }

        public Set<UUID> getMembers() {
            return new HashSet<>(members);
        }

        public Set<UUID> getAssistants() {
            return new HashSet<>(assistants);
        }

        public void addMember(UUID player) {
            members.add(player);
        }

        public void removeMember(UUID player) {
            members.remove(player);
            assistants.remove(player);
        }

        public void addAssistant(UUID player) {
            assistants.add(player);
        }

        public void removeAssistant(UUID player) {
            assistants.remove(player);
        }

        public void addValutes(String valuteId, double amount) {
            valuteBalances.put(valuteId, valuteBalances.getOrDefault(valuteId, 0.0) + amount);
        }

        public void removeValutes(String valuteId, double amount) {
            double current = valuteBalances.getOrDefault(valuteId, 0.0);
            valuteBalances.put(valuteId, Math.max(0.0, current - amount));
        }

        public double getValuteBalance(String valuteId) {
            return valuteBalances.getOrDefault(valuteId, 0.0);
        }

        public Map<String, Double> getAllValuteBalances() {
            return new HashMap<>(valuteBalances);
        }

        public void setName(String name) {
            this.name = name;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("teamId", teamId.toString());
            tag.putString("name", name);
            tag.putString("leader", leader.toString());

            ListTag membersTag = new ListTag();
            for (UUID member : members) {
                membersTag.add(StringTag.valueOf(member.toString()));
            }
            tag.put("members", membersTag);

            ListTag assistantsTag = new ListTag();
            for (UUID assistant : assistants) {
                assistantsTag.add(StringTag.valueOf(assistant.toString()));
            }
            tag.put("assistants", assistantsTag);

            CompoundTag valutesTag = new CompoundTag();
            for (Map.Entry<String, Double> entry : valuteBalances.entrySet()) {
                valutesTag.putDouble(entry.getKey(), entry.getValue());
            }
            tag.put("valutes", valutesTag);

            return tag;
        }

        public static Team load(CompoundTag tag) {
            UUID teamId = tag.getString("teamId").map(UUID::fromString).orElseGet(UUID::randomUUID);
            String name = tag.getString("name").orElse("unknown");
            UUID leader = tag.getString("leader").map(UUID::fromString).orElseGet(UUID::randomUUID);

            Team team = new Team(teamId, name, leader);

            if (tag.contains("members")) {
                ListTag membersTag = tag.getListOrEmpty("members");
                for (int i = 0; i < membersTag.size(); i++) {
                    try {
                        UUID member = UUID.fromString(membersTag.getString(i).orElse(""));
                        if (!member.equals(leader)) {
                            team.addMember(member);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in team members");
                    }
                }
            }

            if (tag.contains("assistants")) {
                ListTag assistantsTag = tag.getListOrEmpty("assistants");
                for (int i = 0; i < assistantsTag.size(); i++) {
                    try {
                        UUID assistant = UUID.fromString(assistantsTag.getString(i).orElse(""));
                        team.addAssistant(assistant);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in team assistants");
                    }
                }
            }

            if (tag.contains("valutes")) {
                CompoundTag valutesTag = tag.getCompoundOrEmpty("valutes");
                for (String valuteId : valutesTag.keySet()) {
                    team.valuteBalances.put(valuteId, valutesTag.getDouble(valuteId).orElse(0.0));
                }
            }

            return team;
        }
    }
}

