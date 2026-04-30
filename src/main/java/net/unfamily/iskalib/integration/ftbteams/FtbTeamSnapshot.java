package net.unfamily.iskalib.integration.ftbteams;

import java.util.Set;
import java.util.UUID;

public record FtbTeamSnapshot(
        String teamKey,
        String displayName,
        UUID ownerId,
        Set<UUID> members,
        Set<UUID> assistants
) {}

