package net.earthcomputer.minimapsync.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record TeleportPayload(String name, @Nullable ResourceKey<Level> dimension) implements CustomPacketPayload {
    public static final Type<TeleportPayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        TeleportPayload::name,
        MinimapSyncStreamCodecs.nullable(ResourceKey.streamCodec(Registries.DIMENSION)),
        TeleportPayload::dimension,
        TeleportPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
