package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IHasModel {
    private Model minimapsync_model = new Model();

    @Override
    public Model minimapsync_model() {
        return minimapsync_model;
    }

    @Override
    public void minimapsync_setModel(Model model) {
        this.minimapsync_model = model;
    }

    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void onLogin(CallbackInfo ci) {
        MinimapSyncClient.onLogin();
    }
}
