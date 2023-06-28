package journeymap.client.ui.waypoint;

import journeymap.client.ui.component.JmUI;
import journeymap.client.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;

public class WaypointEditor extends JmUI {
    private final Waypoint originalWaypoint = null;
    private Waypoint editedWaypoint;

    protected WaypointEditor(String title) {
        super(title);
    }

    @Override
    public void init() {
    }

    protected void drawWaypoint(GuiGraphics guiGraphics, int x, int y) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void save() {
    }

    protected void updateWaypointFromForm() {
    }
}
