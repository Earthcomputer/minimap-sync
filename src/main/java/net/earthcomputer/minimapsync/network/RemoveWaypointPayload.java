package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoveWaypointPayload(String name) implements CustomPacketPayload {
    public static final Type<RemoveWaypointPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "remove_waypoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveWaypointPayload> CODEC = ByteBufCodecs.stringUtf8(256)
        .<RegistryFriendlyByteBuf>cast()
        .map(RemoveWaypointPayload::new, RemoveWaypointPayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
