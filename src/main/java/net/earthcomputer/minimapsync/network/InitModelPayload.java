package net.earthcomputer.minimapsync.network;

import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InitModelPayload(Model model) implements CustomPacketPayload {
    public static final Type<InitModelPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "init_model"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InitModelPayload> CODEC = Model.STREAM_CODEC.map(InitModelPayload::new, InitModelPayload::model);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
