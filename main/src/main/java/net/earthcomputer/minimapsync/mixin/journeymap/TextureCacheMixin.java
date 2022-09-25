package net.earthcomputer.minimapsync.mixin.journeymap;

import journeymap.client.texture.TextureCache;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TextureCache.class)
public class TextureCacheMixin {
    // journeymap hardcoded the working code for themselves leaving the non-working code for
    // plugins... that won't do, mixin so we can use the working code ourselves
    @ModifyConstant(method = "getWaypointIcon", constant = @Constant(stringValue = "journeymap"))
    private static String modifyModId(String modId, ResourceLocation location) {
        if (location.getNamespace().equals("minimapsync")) {
            return "minimapsync";
        } else {
            return modId;
        }
    }
}
