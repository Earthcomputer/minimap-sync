package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiAddWaypoint extends Screen {
    WaypointManager waypointManager;
    protected Waypoint waypoint;

    protected GuiAddWaypoint(Component component) {
        super(component);
    }

    @Override
    public void init() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    public void drawTexturedModalRect(float xCoord, float yCoord, Sprite icon, float widthIn, float heightIn) {
    }
}
