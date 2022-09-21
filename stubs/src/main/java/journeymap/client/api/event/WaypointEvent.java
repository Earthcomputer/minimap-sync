package journeymap.client.api.event;

import journeymap.client.api.display.Waypoint;

public class WaypointEvent extends ClientEvent {
    public final Waypoint waypoint;
    public final Context context;

    public WaypointEvent(Waypoint waypoint, Context context) {
        this.waypoint = waypoint;
        this.context = context;
    }

    public enum Context {
        CREATE,
        UPDATE,
        DELETED,
        READ
    }
}
