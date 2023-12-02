package net.earthcomputer.minimapsync.mixin.xaerominimap;

import net.earthcomputer.minimapsync.client.XaerosMapCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.events.FMLEventHandler;

@Mixin(value = FMLEventHandler.class, remap = false)
public class FMLEventHandlerMixin {
    @Inject(method = "handlePlayerTickStart", at = @At(value = "INVOKE", target = "Lxaero/common/events/FMLEventHandler;playerTickPostOverridable(Lxaero/common/XaeroMinimapSession;)V"))
    private void onPlayerTickStart(CallbackInfo ci) {
        XaerosMapCompat.INSTANCE.onReady();
    }
}
