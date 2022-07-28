package net.earthcomputer.minimapsync.model;

import net.earthcomputer.minimapsync.FriendlyByteBufUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record Waypoint(
    String name,
    @Nullable String description,
    int color,
    Set<ResourceKey<Level>> dimensions,
    BlockPos pos,
    @Nullable UUID author,
    @Nullable String authorName
) {
    public Waypoint(FriendlyByteBuf buf) {
        this(
            buf.readUtf(256),
            FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf),
            buf.readInt(),
            FriendlyByteBufUtil.readCollection(buf, LinkedHashSet::new, buf1 -> FriendlyByteBufUtil.readResourceKey(buf1, Registry.DIMENSION_REGISTRY)),
            buf.readBlockPos(),
            FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUUID),
            FriendlyByteBufUtil.readNullable(buf, buf1 -> buf1.readUtf(16))
        );
    }

    public void toPacket(FriendlyByteBuf buf) {
        buf.writeUtf(name, 256);
        FriendlyByteBufUtil.writeNullable(buf, description, FriendlyByteBuf::writeUtf);
        buf.writeInt(color);
        FriendlyByteBufUtil.writeCollection(buf, dimensions, FriendlyByteBufUtil::writeResourceKey);
        buf.writeBlockPos(pos);
        FriendlyByteBufUtil.writeNullable(buf, author, FriendlyByteBuf::writeUUID);
        FriendlyByteBufUtil.writeNullable(buf, authorName, (buf1, authorName) -> buf1.writeUtf(authorName, 16));
    }

    public Waypoint withDescription(@Nullable String description) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }

    public Waypoint withDimensions(Set<ResourceKey<Level>> dimensions) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }

    public Waypoint withPos(BlockPos pos) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }

    public Waypoint withColor(int color) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }

    public Waypoint withAuthor(@Nullable UUID author) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }

    public Waypoint withAuthorName(@Nullable String authorName) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName);
    }
}
