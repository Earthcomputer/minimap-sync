package journeymap.client.ui.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import journeymap.client.ui.component.JmUI;
import journeymap.client.waypoint.Waypoint;

public class WaypointEditor extends JmUI {
    private final Waypoint originalWaypoint = null;
    private Waypoint editedWaypoint;

    protected WaypointEditor(String title) {
        super(title);
    }

    @Override
    public void init() {
    }

    protected void drawWaypoint(PoseStack mStack, int x, int y) {
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    }

    protected void save() {
    }

    protected void updateWaypointFromForm() {
    }
}
