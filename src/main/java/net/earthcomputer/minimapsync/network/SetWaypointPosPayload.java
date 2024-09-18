package net.earthcomputer.minimapsync.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetWaypointPosPayload(String name, BlockPos pos) implements CustomPacketPayload {
    public static final Type<SetWaypointPosPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "set_waypoint_pos"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointPosPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        SetWaypointPosPayload::name,
        BlockPos.STREAM_CODEC,
        SetWaypointPosPayload::pos,
        SetWaypointPosPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
