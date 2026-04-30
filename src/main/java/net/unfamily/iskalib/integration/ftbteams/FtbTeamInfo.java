package net.unfamily.iskalib.integration.ftbteams;

import java.util.UUID;

public record FtbTeamInfo(
        String teamKey,
        String displayName,
        UUID ownerId
) {}

