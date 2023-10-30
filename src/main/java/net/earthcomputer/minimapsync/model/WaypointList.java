package net.earthcomputer.minimapsync.model;

import net.earthcomputer.minimapsync.FriendlyByteBufUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class WaypointList {
    private final List<Waypoint> waypoints;

    public WaypointList() {
        waypoints = new ArrayList<>();
    }

    public WaypointList(int protocolVersion, FriendlyByteBuf buf) {
        waypoints = FriendlyByteBufUtil.readList(buf, buf1 -> new Waypoint(protocolVersion, buf1));
    }

    public void toPacket(UUID player, int protocolVersion, FriendlyByteBuf buf) {
        FriendlyByteBufUtil.writeCollection(buf, waypoints.stream().filter(wpt -> wpt.isVisibleTo(player)).toList(), (buf2, waypoint) -> waypoint.toPacket(protocolVersion, buf2));
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
}
