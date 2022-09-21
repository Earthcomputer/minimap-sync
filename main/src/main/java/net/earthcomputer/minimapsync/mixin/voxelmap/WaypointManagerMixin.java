package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.client.VoxelMapCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = WaypointManager.class, remap = false)
public class WaypointManagerMixin {
    @Shadow private ArrayList<Waypoint> wayPts;

    @Inject(method = "loadWaypointsExtensible", at = @At("RETURN"))
    private void onLoadWaypoints(CallbackInfoReturnable<Boolean> ci) {
        if (MinimapSyncClient.isCompatibleServer()) {
            VoxelMapCompat.INSTANCE.mergeWaypoints(this.wayPts);
        }
    }
}
