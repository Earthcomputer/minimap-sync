package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.client.VoxelMapCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiWaypoints.class, remap = false)
public class GuiWaypointsMixin {
    @Shadow protected Waypoint selectedWaypoint;

    @Inject(method = "teleportClicked", at = @At("HEAD"), cancellable = true)
    private void onTeleportClicked(CallbackInfo ci) {
        if (MinimapSyncClient.isCompatibleServer() && VoxelMapCompat.INSTANCE.teleport(selectedWaypoint)) {
            Minecraft.getInstance().setScreen(null);
            ci.cancel();
        }
    }
}
