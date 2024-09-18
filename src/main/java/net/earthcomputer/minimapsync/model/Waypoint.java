package net.earthcomputer.minimapsync.model;

import net.earthcomputer.minimapsync.network.MinimapSyncStreamCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

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
    boolean isPrivate,
    WaypointVisibilityType visibilityType
) {
    public static final int MIN_ICON_DIMENSIONS = 16;
    public static final int MAX_ICON_DIMENSIONS = 128;

    public static final StreamCodec<RegistryFriendlyByteBuf, Waypoint> STREAM_CODEC = MinimapSyncStreamCodecs.composite(
        ByteBufCodecs.stringUtf8(256),
        Waypoint::name,
        MinimapSyncStreamCodecs.nullable(ByteBufCodecs.STRING_UTF8),
        Waypoint::description,
        ByteBufCodecs.INT,
        Waypoint::color,
        ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.collection((IntFunction<Set<ResourceKey<Level>>>) LinkedHashSet::new)),
        Waypoint::dimensions,
        BlockPos.STREAM_CODEC,
        Waypoint::pos,
        MinimapSyncStreamCodecs.nullable(MinimapSyncStreamCodecs.UUID),
        Waypoint::author,
        MinimapSyncStreamCodecs.nullable(ByteBufCodecs.stringUtf8(16)),
        Waypoint::authorName,
        MinimapSyncStreamCodecs.nullable(ByteBufCodecs.STRING_UTF8),
        Waypoint::icon,
        MinimapSyncStreamCodecs.LONG,
        Waypoint::creationTime,
        ByteBufCodecs.BOOL,
        Waypoint::isPrivate,
        WaypointVisibilityType.STREAM_CODEC,
        Waypoint::visibilityType,
        Waypoint::new
    );

    public Waypoint {
        if (icon != null) {
            icon = icon.toLowerCase(Locale.ROOT);
        }
    }

    public boolean isVisibleTo(@Nullable ServerPlayer player) {
        return player == null || isVisibleTo(player.getUUID());
    }

    public boolean isVisibleTo(UUID uuid) {
        return !isPrivate || uuid.equals(author);
    }

    public Waypoint withDescription(@Nullable String description) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withDimensions(Set<ResourceKey<Level>> dimensions) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withPos(BlockPos pos) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withColor(int color) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withAuthor(@Nullable UUID author) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withAuthorName(@Nullable String authorName) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withIcon(@Nullable String icon) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withCreationTime(long creationTime) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withPrivate(boolean isPrivate) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }

    public Waypoint withVisibilityType(WaypointVisibilityType visibilityType) {
        return new Waypoint(name, description, color, dimensions, pos, author, authorName, icon, creationTime, isPrivate, visibilityType);
    }
}
