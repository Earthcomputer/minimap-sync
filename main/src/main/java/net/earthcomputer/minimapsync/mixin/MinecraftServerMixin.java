package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements IHasModel {
    private Model minimapsync_model;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        minimapsync_model = Model.load((MinecraftServer) (Object) this);
    }

    @Override
    public Model minimapsync_model() {
        return minimapsync_model;
    }

    @Override
    public void minimapsync_setModel(Model model) {
        minimapsync_model = model;
    }
}
