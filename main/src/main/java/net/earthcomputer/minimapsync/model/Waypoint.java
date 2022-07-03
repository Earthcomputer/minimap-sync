package net.earthcomputer.minimapsync.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Waypoint(
    String name,
    @Nullable String description,
    int color,
    ResourceKey<Level> dimension,
    BlockPos pos,
    @Nullable UUID author
) {
    public Waypoint(FriendlyByteBuf buf) {
        this(
            buf.readUtf(256),
            buf.readBoolean() ? buf.readUtf() : null,
            buf.readInt(),
            ResourceKey.create(Registry.DIMENSION_REGISTRY, buf.readResourceLocation()),
            buf.readBlockPos(),
            buf.readBoolean() ? buf.readUUID() : null
        );
    }

    public void toPacket(FriendlyByteBuf buf) {
        buf.writeUtf(name, 256);
        buf.writeBoolean(description != null);
        if (description != null) {
            buf.writeUtf(description);
        }
        buf.writeInt(color);
        buf.writeResourceLocation(dimension.location());
        buf.writeBlockPos(pos);
        buf.writeBoolean(author != null);
        if (author != null) {
            buf.writeUUID(author);
        }
    }

    public Waypoint withDescription(@Nullable String description) {
        return new Waypoint(name, description, color, dimension, pos, author);
    }

    public Waypoint withPos(BlockPos pos) {
        return new Waypoint(name, description, color, dimension, pos, author);
    }

    public Waypoint withColor(int color) {
        return new Waypoint(name, description, color, dimension, pos, author);
    }
}
