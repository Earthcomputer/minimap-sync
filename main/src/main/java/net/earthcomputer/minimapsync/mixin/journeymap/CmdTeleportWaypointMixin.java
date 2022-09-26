package net.earthcomputer.minimapsync.mixin.journeymap;

import journeymap.client.command.CmdTeleportWaypoint;
import journeymap.client.waypoint.Waypoint;
import net.earthcomputer.minimapsync.client.JourneyMapCompat;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CmdTeleportWaypoint.class)
public class CmdTeleportWaypointMixin {
    @Shadow
    @Final
    Waypoint waypoint;

    @Inject(method = "isPermitted", at = @At("HEAD"), cancellable = true)
    private static void onIsPermitted(Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Model model = Model.get(player.connection);
        cir.setReturnValue(model.teleportRule().canTeleport(player));
    }

    @Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRun(CallbackInfo ci) {
        if (MinimapSyncClient.isCompatibleServer() && JourneyMapCompat.teleport(waypoint)) {
            ci.cancel();
        }
    }
}
