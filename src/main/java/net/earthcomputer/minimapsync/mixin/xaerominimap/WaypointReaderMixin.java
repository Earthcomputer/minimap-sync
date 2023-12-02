package net.earthcomputer.minimapsync.mixin.xaerominimap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.render.WaypointGuiRenderContext;
import xaero.common.minimap.waypoints.render.WaypointReader;

@Mixin(value = WaypointReader.class, remap = false)
public class WaypointReaderMixin {
    @Inject(method = "getRenderBoxRight(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/common/minimap/waypoints/render/WaypointGuiRenderContext;F)I", at = @At("HEAD"), cancellable = true)
    private void modifyRenderBoxForCustomIcon(Waypoint element, WaypointGuiRenderContext context, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (element.getSymbol().startsWith("minimapsync_")) {
            cir.setReturnValue(5);
        }
    }
}
