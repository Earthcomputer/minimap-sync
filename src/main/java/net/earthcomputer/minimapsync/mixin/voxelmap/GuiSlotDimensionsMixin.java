package net.earthcomputer.minimapsync.mixin.voxelmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiSlotDimensions", remap = false)
public class GuiSlotDimensionsMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mamiyaotaru/voxelmap/gui/overridden/GuiSlotMinimap;<init>(IIIII)V", remap = true))
    private static void modifyLocation(Args args) {
        args.set(2, args.<Integer>get(2) - 10);
        args.set(3, args.<Integer>get(3) - 10);
    }
}
