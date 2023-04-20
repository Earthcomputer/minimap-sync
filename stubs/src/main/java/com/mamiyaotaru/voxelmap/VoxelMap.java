package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.DimensionManager;

public abstract class VoxelMap {
    public abstract WaypointManager getWaypointManager();
    public abstract DimensionManager getDimensionManager();
}
