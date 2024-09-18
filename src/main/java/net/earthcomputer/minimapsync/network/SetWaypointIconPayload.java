package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SetWaypointIconPayload(String waypoint, @Nullable String icon) implements CustomPacketPayload {
    public static final Type<SetWaypointIconPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "set_waypoint_icon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointIconPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        SetWaypointIconPayload::waypoint,
        MinimapSyncStreamCodecs.nullable(ByteBufCodecs.stringUtf8(256)),
        SetWaypointIconPayload::icon,
        SetWaypointIconPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
