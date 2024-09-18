package net.earthcomputer.minimapsync.mixin.journeymap;
/*
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.api.display.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "journeymap.client.api.impl.PluginWrapper")
public class PluginWrapperMixin {
    // journeymap's image blending is shite, skip it
    @WrapOperation(method = "getWaypointImageResource", at = @At(value = "INVOKE", target = "Ljourneymap/client/texture/ImageUtil;recolorImage(Lcom/mojang/blaze3d/platform/NativeImage;I)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private NativeImage customRecolorImage(NativeImage image, int color, Operation<NativeImage> original, Waypoint modWaypoint) {
        if ("minimapsync".equals(modWaypoint.getModId())) {
            NativeImage tintedImage = new NativeImage(image.getWidth(), image.getHeight(), false);
            tintedImage.copyFrom(image);
            return tintedImage;
        } else {
            return original.call(image, color);
        }
    }
}
*/