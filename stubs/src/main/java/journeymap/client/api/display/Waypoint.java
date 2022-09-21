package journeymap.client.api.display;

import journeymap.client.api.model.WaypointBase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class Waypoint extends WaypointBase<Waypoint> {
    public Waypoint(String modId, String name, ResourceKey<Level> dimension, BlockPos position) {
    }

    public final String getDimension() {
        return "";
    }

    public final BlockPos getPosition() {
        return BlockPos.ZERO;
    }

    public String[] getDisplayDimensions() {
        return new String[0];
    }

    public Waypoint setPosition(String dimension, BlockPos position) {
        return this;
    }
}
