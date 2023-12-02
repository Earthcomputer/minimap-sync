package net.earthcomputer.minimapsync.mixin.journeymap;

import journeymap.client.waypoint.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Waypoint.class)
public interface InternalWaypointAccessor {
    @Accessor
    void setDisplayId(String displayId);
}
