package net.earthcomputer.minimapsync.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntFunction;

public record SetWaypointDimensionsPayload(String name, Set<ResourceKey<Level>> dimensions) implements CustomPacketPayload {
    public static final Type<SetWaypointDimensionsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("minimapsync", "set_waypoint_dimensions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointDimensionsPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256),
        SetWaypointDimensionsPayload::name,
        ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.collection((IntFunction<Set<ResourceKey<Level>>>) LinkedHashSet::new)),
        SetWaypointDimensionsPayload::dimensions,
        SetWaypointDimensionsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
