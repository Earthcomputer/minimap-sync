package net.earthcomputer.minimapsync.model;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

public enum WaypointVisibilityType {
    LOCAL, GLOBAL, WORLD_MAP_LOCAL, WORLD_MAP_GLOBAL,
    ;

    private static final IntFunction<WaypointVisibilityType> BY_ID = ByIdMap.continuous(WaypointVisibilityType::ordinal, WaypointVisibilityType.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, WaypointVisibilityType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, WaypointVisibilityType::ordinal);
}
