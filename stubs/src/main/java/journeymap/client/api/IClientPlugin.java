package journeymap.client.api;

import journeymap.client.api.event.ClientEvent;

public interface IClientPlugin {
    void initialize(IClientAPI api);
    String getModId();
    void onEvent(ClientEvent event);
}
