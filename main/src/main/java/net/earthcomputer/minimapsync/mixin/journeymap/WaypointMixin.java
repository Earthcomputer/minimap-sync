package net.earthcomputer.minimapsync.mixin.journeymap;

import journeymap.client.waypoint.Waypoint;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Waypoint.class)
public abstract class WaypointMixin {
    @Shadow
    protected String icon;
    @Shadow
    protected Integer iconColor;
    @Shadow
    public abstract Waypoint setIcon(ResourceLocation iconResource);
    @Shadow
    public abstract void setIconColor(Integer iconColor);

    @Inject(method = "<init>(Ljourneymap/client/waypoint/Waypoint;)V", at = @At("RETURN"))
    private void copyIcon(Waypoint other, CallbackInfo ci) {
        String icon = ((WaypointMixin) (Object) other).icon;
        Integer iconColor = ((WaypointMixin) (Object) other).iconColor;
        ResourceLocation lcoation = icon == null ? null : ResourceLocation.tryParse(icon);
        if (lcoation != null) {
            setIconColor(iconColor);
            setIcon(lcoation);
        }
    }
}
