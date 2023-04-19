package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class PopupGuiButton extends Button {
    public PopupGuiButton(int x, int y, int widthIn, int heightIn, Component buttonText, OnPress pressAction, IPopupGuiScreen parentScreen) {
        super(x, y, widthIn, heightIn, buttonText, pressAction, DEFAULT_NARRATION);
    }
}
