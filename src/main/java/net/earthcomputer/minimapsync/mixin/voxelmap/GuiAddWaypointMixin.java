package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.gui.GuiAddWaypoint;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.earthcomputer.minimapsync.client.ChooseIconScreen;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.client.VoxelMapCompat;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = GuiAddWaypoint.class, remap = false)
public abstract class GuiAddWaypointMixin extends Screen {
    @Shadow
    WaypointManager waypointManager;
    @Shadow
    protected Waypoint waypoint;

    @Shadow
    public abstract void drawTexturedModalRect(float xCoord, float yCoord, Sprite icon, float widthIn, float heightIn);

    protected GuiAddWaypointMixin(Component component) {
        super(component);
    }

    @ModifyArg(
        method = "init",
        remap = true,
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=minimap.waypoints.sortbyicon", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lcom/mamiyaotaru/voxelmap/gui/overridden/PopupGuiButton;<init>(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;Lcom/mamiyaotaru/voxelmap/gui/overridden/IPopupGuiScreen;)V", ordinal = 0))
    private Button.OnPress modifySelectIconAction(Button.OnPress action) {
        if (MinimapSyncClient.isCompatibleServer()) {
            return button -> this.minecraft.setScreen(new ChooseIconScreen(
                this,
                VoxelMapCompat.undecorateIconNameSuffix(waypoint.imageSuffix),
                result -> waypoint.imageSuffix = result == null ? "" : VoxelMapCompat.decorateIconNameSuffix(result),
                icon -> {
                    Sprite sprite = waypointManager.getTextureAtlas().getAtlasSprite(VoxelMapCompat.decorateIconName(icon));
                    if (sprite == null || sprite == waypointManager.getTextureAtlas().getMissingImage()) {
                        return null;
                    }
                    return (poseStack, x, y, width, height, red, green, blue) -> {
                        RenderSystem.setShader(GameRenderer::getPositionTexShader);
                        RenderSystem.setShaderTexture(0, waypointManager.getTextureAtlas().getId());
                        RenderSystem.setShaderColor(red, green, blue, 1);
                        drawTexturedModalRect(x, y, sprite, width, height);
                    };
                }
            ));
        } else {
            return action;
        }
    }

    @ModifyVariable(method = "render", remap = true, at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private TextureAtlas modifyAtlas(TextureAtlas atlas) {
        if (MinimapSyncClient.isCompatibleServer()) {
            return waypointManager.getTextureAtlas();
        } else {
            return atlas;
        }
    }
}
