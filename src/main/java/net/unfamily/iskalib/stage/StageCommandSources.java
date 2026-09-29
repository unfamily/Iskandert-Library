package net.unfamily.iskalib.stage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player-relative command sources ({@code ~ ~ ~} resolves at the player).
 */
public final class StageCommandSources {
    private StageCommandSources() {}

    public static CommandSourceStack at(ServerPlayer player) {
        return player.createCommandSourceStack()
                .withPosition(player.position())
                .withEntity(player)
                .withRotation(player.getRotationVector());
    }
}
