package journeymap.client.api;

import journeymap.client.api.display.Displayable;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;

import java.util.EnumSet;
import java.util.List;

public interface IClientAPI {
    void subscribe(String var1, EnumSet<ClientEvent.Type> var2);

    List<Waypoint> getAllWaypoints();

    void show(Displayable displayable);

    void remove(Displayable displayable);
}
