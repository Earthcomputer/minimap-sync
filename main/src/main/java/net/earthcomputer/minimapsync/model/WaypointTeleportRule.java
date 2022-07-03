package net.earthcomputer.minimapsync.model;

import net.minecraft.server.level.ServerPlayer;

public enum WaypointTeleportRule {
    NEVER,
    CREATIVE_PLAYERS,
    CREATIVE_AND_SPECTATOR_PLAYERS,
    OP_PLAYERS,
    ALWAYS,
    ;

    public boolean canTeleport(ServerPlayer player) {
        return switch (this) {
            case NEVER -> false;
            case CREATIVE_PLAYERS -> player.isCreative();
            case CREATIVE_AND_SPECTATOR_PLAYERS -> player.isCreative() || player.isSpectator();
            case OP_PLAYERS -> player.hasPermissions(2);
            case ALWAYS -> true;
        };
    }
}
