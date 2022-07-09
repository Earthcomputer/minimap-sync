package net.earthcomputer.minimapsync.client;

import net.earthcomputer.minimapsync.client.IMinimapCompat;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;

public enum JourneyMapCompat implements IMinimapCompat {
    INSTANCE;

    @Override
    public void initModel(ClientPacketListener listener, Model model) {

    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {

    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {

    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {

    }

    @Override
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {

    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {

    }

    @Override
    public void setWaypointDescription(ClientPacketListener handler, String name, String description) {

    }

    @Override
    public void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule) {

    }
}
