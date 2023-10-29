package net.earthcomputer.minimapsync.mixin.journeymap;

import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.api.display.Waypoint;
import journeymap.client.texture.ImageUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "journeymap.client.api.impl.PluginWrapper")
public class PluginWrapperMixin {
    // journeymap's image blending is shite, skip it
    @Redirect(method = "getWaypointImageResource", at = @At(value = "INVOKE", target = "Ljourneymap/client/texture/ImageUtil;recolorImage(Lcom/mojang/blaze3d/platform/NativeImage;I)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private NativeImage customRecolorImage(NativeImage image, int color, Waypoint modWaypoint) {
        if ("minimapsync".equals(modWaypoint.getModId())) {
            NativeImage tintedImage = new NativeImage(image.getWidth(), image.getHeight(), false);
            tintedImage.copyFrom(image);
            return tintedImage;
        } else {
            return ImageUtil.recolorImage(image, color);
        }
    }
}
