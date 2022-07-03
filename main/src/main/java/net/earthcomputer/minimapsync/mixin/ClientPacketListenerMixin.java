package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;

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
}
