package journeymap.client.api.model;

import journeymap.client.api.display.Displayable;

@SuppressWarnings("unchecked")
public class WaypointBase<T extends WaypointBase<T>> extends Displayable {
    public final String getName() {
        return "";
    }

    public final T setDisplayDimensions(String... dimensions) {
        return (T) this;
    }

    public final Integer getColor() {
        return null;
    }

    public final T setColor(int color) {
        return (T) this;
    }

    public MapImage getIcon() {
        return null;
    }
}
