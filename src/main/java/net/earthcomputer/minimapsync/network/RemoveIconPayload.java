package net.earthcomputer.minimapsync.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoveIconPayload(String name) implements CustomPacketPayload {
    public static final Type<RemoveIconPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "remove_icon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveIconPayload> CODEC = ByteBufCodecs.stringUtf8(256)
        .<RegistryFriendlyByteBuf>cast()
        .map(RemoveIconPayload::new, RemoveIconPayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
