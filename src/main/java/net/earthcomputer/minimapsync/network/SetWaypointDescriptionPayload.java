package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SetWaypointDescriptionPayload(String name, @Nullable String description) implements CustomPacketPayload {
    public static final Type<SetWaypointDescriptionPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "set_waypoint_description"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointDescriptionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        SetWaypointDescriptionPayload::name,
        MinimapSyncStreamCodecs.nullable(ByteBufCodecs.STRING_UTF8),
        SetWaypointDescriptionPayload::description,
        SetWaypointDescriptionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
