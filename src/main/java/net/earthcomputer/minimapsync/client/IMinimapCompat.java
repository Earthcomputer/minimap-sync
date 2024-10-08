package net.earthcomputer.minimapsync.client;

import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface IMinimapCompat {
    boolean isReady();
    void initModel(ClientPacketListener listener, Model model);
    void addWaypoint(ClientPacketListener listener, Waypoint waypoint);
    void removeWaypoint(ClientPacketListener listener, String name);
    void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions);
    void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos);
    void setWaypointColor(ClientPacketListener handler, String name, int color);
    void setWaypointDescription(ClientPacketListener handler, String name, @Nullable String description);
    void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule);
    void addIcon(ClientPacketListener handler, String name, byte[] icon);
    void removeIcon(ClientPacketListener handler, String name);
    void setWaypointIcon(ClientPacketListener handler, String waypoint, @Nullable String icon);
}
