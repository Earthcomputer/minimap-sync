package net.earthcomputer.minimapsync.network;

import net.earthcomputer.minimapsync.model.Waypoint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddWaypointPayload(Waypoint waypoint) implements CustomPacketPayload {
    public static final Type<AddWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "add_waypoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddWaypointPayload> CODEC = Waypoint.STREAM_CODEC.map(AddWaypointPayload::new, AddWaypointPayload::waypoint);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
