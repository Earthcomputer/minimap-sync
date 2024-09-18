package net.earthcomputer.minimapsync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record SplitPacketPayload(
    Type<?> innerType,
    boolean isLastPayload,
    byte[] data
) implements CustomPacketPayload {
    public static final Type<SplitPacketPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "split_packet"));
    public static final StreamCodec<FriendlyByteBuf, SplitPacketPayload> CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC.map(id -> Objects.requireNonNull(PacketSplitter.getType(id), () -> "No split packet type registered for " + id), Type::id),
        SplitPacketPayload::innerType,
        ByteBufCodecs.BOOL,
        SplitPacketPayload::isLastPayload,
        ByteBufCodecs.BYTE_ARRAY,
        SplitPacketPayload::data,
        SplitPacketPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
