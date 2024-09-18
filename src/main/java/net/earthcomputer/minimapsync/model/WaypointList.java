package net.earthcomputer.minimapsync.model;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WaypointList {
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointList> STREAM_CODEC = Waypoint.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(WaypointList::new, waypointList -> waypointList.waypoints);

    private final List<Waypoint> waypoints;

    public WaypointList() {
        this(new ArrayList<>());
    }

    private WaypointList(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
    }

    public WaypointList filterForPlayer(UUID player) {
        return new WaypointList(waypoints.stream().filter(wpt -> wpt.isVisibleTo(player)).collect(Collectors.toCollection(ArrayList::new)));
    }

    @Nullable
    public Waypoint getWaypoint(String name) {
        for (Waypoint waypoint : waypoints) {
            if (name.equals(waypoint.name())) {
                return waypoint;
            }
        }
        return null;
    }

    public boolean addWaypoint(Waypoint waypoint) {
        for (Waypoint waypoint1 : waypoints) {
            if (waypoint1.name().equals(waypoint.name())) {
                return false;
            }
        }
        waypoints.add(waypoint);
        return true;
    }

    public boolean removeWaypoint(@Nullable ServerPlayer permissionCheck, String name) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.remove(waypoint);
                return true;
            }
        }
        return false;
    }

    public boolean setWaypointDimensions(@Nullable ServerPlayer permissionCheck, String name, Set<ResourceKey<Level>> dimensions) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.set(i, waypoint.withDimensions(dimensions));
                return true;
            }
        }
        return false;
    }

    public boolean setPos(@Nullable ServerPlayer permissionCheck, String name, BlockPos pos) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.set(i, waypoint.withPos(pos));
                return true;
            }
        }
        return false;
    }

    public boolean setColor(@Nullable ServerPlayer permissionCheck, String name, int color) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.set(i, waypoint.withColor(color));
                return true;
            }
        }
        return false;
    }

    public boolean setDescription(@Nullable ServerPlayer permissionCheck, String name, @Nullable String description) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.set(i, waypoint.withDescription(description));
                return true;
            }
        }
        return false;
    }

    public boolean setIcon(@Nullable ServerPlayer permissionCheck, String name, @Nullable String icon) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.name().equals(name) && waypoint.isVisibleTo(permissionCheck)) {
                waypoints.set(i, waypoint.withIcon(icon));
                return true;
            }
        }
        return false;
    }

    public Stream<Waypoint> getWaypoints(@Nullable UUID author) {
        if (author == null) {
            return waypoints.stream();
        } else {
            return waypoints.stream().filter(waypoint -> author.equals(waypoint.author()));
        }
    }

    void setAllToCurrentTime() {
        long time = System.currentTimeMillis();
        waypoints.replaceAll(waypoint -> waypoint.withCreationTime(time));
    }

    void setAllToLocalVisibility() {
        waypoints.replaceAll(waypoint -> waypoint.withVisibilityType(WaypointVisibilityType.LOCAL));
    }
}
