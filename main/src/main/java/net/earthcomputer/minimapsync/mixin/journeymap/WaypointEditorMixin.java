package net.earthcomputer.minimapsync.mixin.journeymap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import journeymap.client.texture.DynamicTextureImpl;
import journeymap.client.texture.SimpleTextureImpl;
import journeymap.client.texture.Texture;
import journeymap.client.ui.component.Button;
import journeymap.client.ui.component.JmUI;
import journeymap.client.ui.waypoint.WaypointEditor;
import journeymap.client.waypoint.Waypoint;
import journeymap.client.waypoint.WaypointStore;
import net.earthcomputer.minimapsync.client.ChooseIconScreen;
import net.earthcomputer.minimapsync.client.IconSelectionList;
import net.earthcomputer.minimapsync.client.JourneyMapCompat;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Objects;

@Mixin(WaypointEditor.class)
public class WaypointEditorMixin extends JmUI {
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
    protected void drawWaypoint(PoseStack mStack, int x, int y) {
    }

    @Unique
    private Button minimapsync_iconButton;
    @Unique
    @Nullable
    private String minimapsync_iconName;
    @Unique
    private boolean minimapsync_iconNeedsClosing;
    @Unique
    private boolean minimapsync_isRedrawingWaypoint;

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
        minimapsync_iconName = waypoint == null ? null : waypoint.icon();
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;getRenderables()Ljava/util/List;", ordinal = 1))
    private void addIconButton(CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        minimapsync_iconButton = addRenderableWidget(
            new Button(20, 20, "", button -> minecraft.setScreen(new ChooseIconScreen(
                this,
                minimapsync_iconName,
                newIcon -> {
                    if (Objects.equals(newIcon, minimapsync_iconName)) {
                        return;
                    }
                    minimapsync_iconName = newIcon;
                    if (newIcon == null) {
                        this.wpTexture = new SimpleTextureImpl(JourneyMapCompat.ICON_NORMAL);
                        minimapsync_iconNeedsClosing = false;
                        return;
                    }
                    byte[] bytes = Model.get(Minecraft.getInstance().getConnection()).icons().get(newIcon);
                    if (bytes == null) {
                        return;
                    }
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        NativeImage image = NativeImage.read(stack.bytes(bytes));
                        this.wpTexture = new DynamicTextureImpl(image);
                        minimapsync_iconNeedsClosing = true;
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
        getRenderables().add(minimapsync_iconButton);
    }

    @Inject(method = "drawWaypoint", at = @At("HEAD"), cancellable = true)
    private void onLayoutButtons(PoseStack mStack, int x, int y, CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        if (!minimapsync_isRedrawingWaypoint) {
            minimapsync_iconButton.setPosition(x - 2, y - 10);
            ci.cancel();
        }
    }

    @Inject(method = {"render", "method_25394"}, at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;drawTitle(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void redrawWaypoint(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        minimapsync_isRedrawingWaypoint = true;
        try {
            drawWaypoint(poseStack, minimapsync_iconButton.getX() + 2, minimapsync_iconButton.getY() + 10);
        } finally {
            minimapsync_isRedrawingWaypoint = false;
        }
    }

    @Inject(method = "save", at = @At(value = "INVOKE", target = "Ljourneymap/client/ui/waypoint/WaypointEditor;updateWaypointFromForm()V"))
    private void onSave(CallbackInfo ci) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }
        Model model = Model.get(Minecraft.getInstance().getConnection());
        String name = originalWaypoint.getName();
        model.waypoints().setIcon(name, minimapsync_iconName);
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
        if (MinimapSyncClient.isCompatibleServer() && minimapsync_iconNeedsClosing) {
            wpTexture.remove();
        }
        super.removed();
    }
}
