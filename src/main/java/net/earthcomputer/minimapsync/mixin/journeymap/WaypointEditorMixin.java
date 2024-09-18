package net.earthcomputer.minimapsync.mixin.journeymap;
/*
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import journeymap.client.texture.DynamicTextureImpl;
import journeymap.client.texture.SimpleTextureImpl;
import journeymap.client.texture.Texture;
import journeymap.client.ui.component.Button;
import journeymap.client.ui.component.CheckBox;
import journeymap.client.ui.component.JmUI;
import journeymap.client.ui.component.ScrollPane;
import journeymap.client.ui.component.TextBox;
import journeymap.client.ui.waypoint.WaypointEditor;
import journeymap.client.waypoint.Waypoint;
import journeymap.client.waypoint.WaypointStore;
import net.earthcomputer.minimapsync.client.ChooseIconScreen;
import net.earthcomputer.minimapsync.client.IconSelectionList;
import net.earthcomputer.minimapsync.client.JourneyMapCompat;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Objects;

@Mixin(value = WaypointEditor.class, remap = false)
public abstract class WaypointEditorMixin extends JmUI {
    @Shadow
    @Final
    @Mutable
    private Texture wpTexture;
    @Shadow
    @Final
    @Mutable
    private Waypoint originalWaypoint;
    @Shadow
    private Waypoint editedWaypoint;
    @Shadow
    private Button buttonReset;
    @Shadow
    private ScrollPane dimScrollPane;
    @Shadow @Final
    private boolean isNew;
    @Shadow
    private TextBox fieldName;

    @Shadow
    protected void drawWaypoint(PoseStack poseStack, int x, int y) {
    }

    @Unique
    private Button iconButton;
    @Unique
    @Nullable
    private String iconName;
    @Unique
    private boolean iconNeedsClosing;
    @Unique
    private boolean isRedrawingWaypoint;
    @Unique
    private CheckBox isPrivateCheckbox;

    protected WaypointEditorMixin(String title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        Model model = Model.get(Minecraft.getInstance().getConnection());
        var waypoint = model.waypoints().getWaypoint(originalWaypoint.getName());
        iconName = waypoint == null ? null : waypoint.icon();
    }

    @Inject(method = "init", remap = true, at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;getRenderables()Ljava/util/List;", ordinal = 1, remap = false))
    private void addIconButton(CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        iconButton = addRenderableWidget(
            new Button(20, 20, "", button -> minecraft.setScreen(new ChooseIconScreen(
                this,
                iconName,
                newIcon -> {
                    if (Objects.equals(newIcon, iconName)) {
                        return;
                    }
                    iconName = newIcon;
                    if (newIcon == null) {
                        this.wpTexture = new SimpleTextureImpl(JourneyMapCompat.ICON_NORMAL);
                        iconNeedsClosing = false;
                        return;
                    }
                    byte[] bytes = Model.get(Minecraft.getInstance().getConnection()).icons().get(newIcon);
                    if (bytes == null) {
                        return;
                    }
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        NativeImage image = NativeImage.read(stack.bytes(bytes));
                        this.wpTexture = new DynamicTextureImpl(image);
                        iconNeedsClosing = true;
                    } catch (IOException ignore) {
                    }
                },
                iconName -> {
                    byte[] bytes = Model.get(Minecraft.getInstance().getConnection()).icons().get(iconName);
                    if (bytes == null) {
                        return null;
                    }
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        NativeImage image = NativeImage.read(stack.bytes(bytes));
                        return IconSelectionList.IconRenderer.makeRendererFromImage(iconName, image);
                    } catch (IOException e) {
                        return null;
                    }
                }
            )))
        );
        getRenderables().add(iconButton);

        if (isNew) {
            isPrivateCheckbox = addRenderableWidget(new CheckBox(I18n.get("minimapsync.private"), isPrivateCheckbox != null && isPrivateCheckbox.getToggled()));
            getRenderables().add(isPrivateCheckbox);
        }
    }

    @ModifyArg(method = "layoutButtons", at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/component/ScrollPane;setDimensions(IIIIII)V", remap = true), index = 1)
    private int modifyDimScrollPaneHeight(int oldHeight) {
        return oldHeight - buttonReset.getHeight() - 4;
    }

    @Inject(method = "layoutButtons", at = @At("RETURN"))
    private void onLayoutButtons(CallbackInfo ci) {
        if (isNew) {
            isPrivateCheckbox.setPosition(dimScrollPane.getX(), buttonReset.getY());
            isPrivateCheckbox.setWidth(dimScrollPane.getWidth());
            isPrivateCheckbox.setHeight(buttonReset.getHeight());
        }
    }

    @Inject(method = "drawWaypoint", at = @At("HEAD"), cancellable = true)
    private void onLayoutButtons(PoseStack poseStack, int x, int y, CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        if (!isRedrawingWaypoint) {
            iconButton.setPosition(x - 2, y - 10);
            ci.cancel();
        }
    }

    @Inject(method = "render", remap = true, at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;drawTitle(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void redrawWaypoint(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        isRedrawingWaypoint = true;
        try {
            drawWaypoint(guiGraphics.pose(), iconButton.getX() + 2, iconButton.getY() + 10);
        } finally {
            isRedrawingWaypoint = false;
        }
    }

    @Inject(method = "save", at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;updateWaypointFromForm()V"))
    private void onSave(CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }

        if (isNew) {
            JourneyMapCompat.instance().setWaypointIsPrivate(fieldName.getValue(), isPrivateCheckbox.getToggled());
        }

        Model model = Model.get(Minecraft.getInstance().getConnection());
        String name = originalWaypoint.getName();
        model.waypoints().setIcon(null, name, iconName);
        var waypoint = model.waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }
        MinimapSyncClient.onSetWaypointIcon(null, waypoint);

        for (Waypoint substWaypoint : WaypointStore.INSTANCE.getAll()) {
            if (substWaypoint.getName().equals(name)) {
                this.originalWaypoint = substWaypoint;
                this.editedWaypoint.setIconColor(substWaypoint.getIconColor());
                this.editedWaypoint.setIcon(substWaypoint.getIcon());
                break;
            }
        }
    }

    @Override
    public void removed() {
        if (MinimapSyncClient.isCompatibleServer() && iconNeedsClosing) {
            wpTexture.remove();
        }
        super.removed();
    }
}
*/