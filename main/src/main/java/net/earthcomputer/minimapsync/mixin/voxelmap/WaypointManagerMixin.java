package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.WaypointManager;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.client.VoxelMapCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaypointManager.class)
public class WaypointManagerMixin {
    @Inject(method = "loadWaypoints", at = @At("RETURN"))
    private void onLoadWaypoints(CallbackInfo ci) {
        if (MinimapSyncClient.isCompatibleServer()) {
            VoxelMapCompat.INSTANCE.mergeWaypoints();
        }
    }
}
