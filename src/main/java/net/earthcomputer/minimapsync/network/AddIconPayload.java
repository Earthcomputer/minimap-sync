package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddIconPayload(String name, byte[] icon) implements CustomPacketPayload {
    public static final Type<AddIconPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "add_icon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddIconPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        AddIconPayload::name,
        ByteBufCodecs.BYTE_ARRAY,
        AddIconPayload::icon,
        AddIconPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
