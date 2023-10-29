package net.earthcomputer.minimapsync.mixin.journeymap;

import journeymap.common.log.LogFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LogFormatter.class, remap = false)
public class LogFormatterMixin {
    // Fix an infinite loop that makes plugin initialization hard to debug sometimes
    @Inject(method = "checkErrors", at = @At(value = "CONSTANT", args = "classValue=java/lang/StackOverflowError"), cancellable = true)
    private static void fixCheckErrors(Throwable thrown, CallbackInfo ci) {
        if (!(thrown instanceof Exception) && !(thrown instanceof StackOverflowError) && !(thrown instanceof OutOfMemoryError) && !(thrown instanceof LinkageError)) {
            ci.cancel();
        }
    }
}
