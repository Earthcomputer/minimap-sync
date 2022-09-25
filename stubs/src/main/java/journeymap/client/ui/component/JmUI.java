package journeymap.client.ui.component;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public abstract class JmUI extends Screen {
    protected JmUI(String title) {
        super(Component.nullToEmpty(title));
    }

    public List<Widget> getRenderables() {
        return Collections.emptyList();
    }

    protected void drawTitle(PoseStack mStack) {
    }
}
