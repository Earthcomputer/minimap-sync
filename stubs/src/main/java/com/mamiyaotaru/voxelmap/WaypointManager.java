package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.Waypoint;

import java.util.ArrayList;

public abstract class WaypointManager {
    public abstract ArrayList<Waypoint> getWaypoints();
    public abstract void deleteWaypoint(Waypoint waypoint);
    public abstract void saveWaypoints();
    public abstract void addWaypoint(Waypoint waypoint);
    public abstract String getCurrentSubworldDescriptor(boolean withCodes);
    public abstract TextureAtlas getTextureAtlas();
}
