package net.earthcomputer.minimapsync.model;

import net.earthcomputer.minimapsync.FriendlyByteBufUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
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
    @Nullable String authorName,
    @Nullable String icon,
    long creationTime,
    boolean isPrivate
) {
    public static final int MIN_ICON_DIMENSIONS = 16;
    public static final int MAX_ICON_DIMENSIONS = 128;

    public Waypoint(int protocolVersion, FriendlyByteBuf buf) {
        this(
            buf.readUtf(256),
            FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf),
            buf.readInt(),
            FriendlyByteBufUtil.readCollection(buf, LinkedHashSet::new, buf1 -> FriendlyByteBufUtil.readResourceKey(buf1, Registry.DIMENSION_REGISTRY)),
            buf.readBlockPos(),
            FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUUID),
            FriendlyByteBufUtil.readNullable(buf, buf1 -> buf1.readUtf(16)),
            protocolVersion >= 1 ? FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf) : null,
            protocolVersion >= 2 ? buf.readLong() : System.currentTimeMillis(),
            protocolVersion >= 3 && buf.readBoolean()
        );
    }

    public void toPacket(int protocolVersion, FriendlyByteBuf buf) {
        buf.writeUtf(name, 256);
        FriendlyByteBufUtil.writeNullable(buf, description, FriendlyByteBuf::writeUtf);
        buf.writeInt(color);
        FriendlyByteBufUtil.writeCollection(buf, dimensions, FriendlyByteBufUtil::writeResourceKey);
        buf.writeBlockPos(pos);
        FriendlyByteBufUtil.writeNullable(buf, author, FriendlyByteBuf::writeUUID);
        FriendlyByteBufUtil.writeNullable(buf, authorName, (buf1, authorName) -> buf1.writeUtf(authorName, 16));
        if (protocolVersion >= 1) {
            FriendlyByteBufUtil.writeNullable(buf, icon, FriendlyByteBuf::writeUtf);
        }
        if (protocolVersion >= 2) {
            buf.writeLong(creationTime);
        }
        if (protocolVersion >= 3) {
            buf.writeBoolean(isPrivate);
        }
    }

    public boolean isVisibleTo(@Nullable ServerPlayer player) {
        return player == null || isVisibleTo(player.getUUID());
    }

    public boolean isVisibleTo(UUID uuid) {
        return !isPrivate || uuid.equals(author);
    }

    public Waypoint withDescription(@Nullable String description) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withDimensions(Set<ResourceKey<Level>> dimensions) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withPos(BlockPos pos) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withColor(int color) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withAuthor(@Nullable UUID author) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withAuthorName(@Nullable String authorName) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withIcon(@Nullable String icon) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withCreationTime(long creationTime) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }

    public Waypoint withPrivate(boolean isPrivate) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate);
    }
}
