package net.earthcomputer.minimapsync.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.textures.IIconCreator;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.client.VoxelMapCompat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "newWorld", at = @At("RETURN"))
    private void onNewWorld(Level level, CallbackInfo ci) {
        if (level != null) {
            VoxelMapCompat.INSTANCE.onReady();
        }
    }

    @ModifyVariable(method = "onResourceManagerReload", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private IIconCreator modifyIconCreator(IIconCreator creator) {
        if (MinimapSyncClient.isCompatibleServer()) {
            return atlas -> {
                creator.addIcons(atlas);
                VoxelMapCompat.INSTANCE.registerIconsToAtlas(atlas);
            };
        }
        return creator;
    }
}
