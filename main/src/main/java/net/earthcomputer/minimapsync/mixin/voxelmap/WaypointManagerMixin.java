package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.WaypointManager;
import net.earthcomputer.minimapsync.MinimapSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaypointManager.class)
public class WaypointManagerMixin {
    @Inject(method = "loadWaypoints", at = @At("HEAD"), cancellable = true)
    private void onLoadWaypoints(CallbackInfo ci) {
        if (ClientPlayNetworking.canSend(MinimapSync.ADD_WAYPOINT)) {
            ci.cancel();
        }
    }
}
