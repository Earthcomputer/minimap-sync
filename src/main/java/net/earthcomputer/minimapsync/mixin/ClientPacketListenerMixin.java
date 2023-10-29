package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IHasModel, IHasProtocolVersion {
    @Unique
    private Model minimapsync_model = new Model();
    @Unique
    private int minimapsync_protocolVersion;

    @Override
    public Model minimapsync_model() {
        return minimapsync_model;
    }

    @Override
    public void minimapsync_setModel(Model model) {
        this.minimapsync_model = model;
    }

    @Override
    public int minimapsync_getProtocolVersion() {
        return minimapsync_protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        minimapsync_protocolVersion = protocolVersion;
    }
}
