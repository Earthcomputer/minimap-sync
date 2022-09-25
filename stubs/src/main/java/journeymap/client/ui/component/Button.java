package journeymap.client.ui.component;

import net.minecraft.network.chat.Component;

public class Button extends net.minecraft.client.gui.components.Button {
    public Button(int width, int height, String label, OnPress onPress) {
        super(0, 0, width, height, Component.nullToEmpty(label), onPress);
    }

    public void setPosition(int x, int y) {
    }
}
