package com.mamiyaotaru.voxelmap.interfaces;

import com.mamiyaotaru.voxelmap.util.Waypoint;

import java.util.ArrayList;

public interface IWaypointManager {
    ArrayList<Waypoint> getWaypoints();
    void deleteWaypoint(Waypoint waypoint);
    void saveWaypoints();
    void addWaypoint(Waypoint waypoint);
    String getCurrentSubworldDescriptor(boolean withCodes);
}
