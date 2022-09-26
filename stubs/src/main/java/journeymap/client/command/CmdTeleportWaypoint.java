package journeymap.client.command;

import journeymap.client.waypoint.Waypoint;
import net.minecraft.client.Minecraft;

public class CmdTeleportWaypoint {
    final Waypoint waypoint = null;

    public static boolean isPermitted(Minecraft mc) {
        return false;
    }

    public void run() {
    }
}
