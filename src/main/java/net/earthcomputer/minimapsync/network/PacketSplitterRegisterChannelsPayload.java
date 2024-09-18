package net.earthcomputer.minimapsync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

public record PacketSplitterRegisterChannelsPayload(Set<ResourceLocation> channels) implements CustomPacketPayload {
    public static final Type<PacketSplitterRegisterChannelsPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "packet_splitter_register_channels"));
    public static final StreamCodec<FriendlyByteBuf, PacketSplitterRegisterChannelsPayload> CODEC = ResourceLocation.STREAM_CODEC
        .apply(ByteBufCodecs.collection((IntFunction<Set<ResourceLocation>>) HashSet::new))
        .<FriendlyByteBuf>cast()
        .map(PacketSplitterRegisterChannelsPayload::new, PacketSplitterRegisterChannelsPayload::channels);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
