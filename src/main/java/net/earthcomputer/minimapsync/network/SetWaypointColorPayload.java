package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetWaypointColorPayload(String name, int color) implements CustomPacketPayload {
    public static final Type<SetWaypointColorPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "set_waypoint_color"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointColorPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        SetWaypointColorPayload::name,
        ByteBufCodecs.INT,
        SetWaypointColorPayload::color,
        SetWaypointColorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
