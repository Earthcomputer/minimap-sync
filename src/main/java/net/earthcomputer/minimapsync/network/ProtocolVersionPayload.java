package net.earthcomputer.minimapsync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProtocolVersionPayload(int protocolVersion) implements CustomPacketPayload {
    public static final Type<ProtocolVersionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "protocol_version"));
    public static final StreamCodec<FriendlyByteBuf, ProtocolVersionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ProtocolVersionPayload::protocolVersion,
        ProtocolVersionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
