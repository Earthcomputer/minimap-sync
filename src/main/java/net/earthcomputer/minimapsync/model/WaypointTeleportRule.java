package net.earthcomputer.minimapsync.model;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.player.Player;

import java.util.function.IntFunction;

public enum WaypointTeleportRule {
    NEVER,
    CREATIVE_PLAYERS,
    CREATIVE_AND_SPECTATOR_PLAYERS,
    OP_PLAYERS,
    ALWAYS,
    ;

    private static final IntFunction<WaypointTeleportRule> BY_ID = ByIdMap.continuous(WaypointTeleportRule::ordinal, WaypointTeleportRule.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, WaypointTeleportRule> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, WaypointTeleportRule::ordinal);

    public boolean canTeleport(Player player) {
        return switch (this) {
            case NEVER -> false;
            case CREATIVE_PLAYERS -> player.isCreative();
            case CREATIVE_AND_SPECTATOR_PLAYERS -> player.isCreative() || player.isSpectator();
            case OP_PLAYERS -> player.hasPermissions(2);
            case ALWAYS -> true;
        };
    }
}
