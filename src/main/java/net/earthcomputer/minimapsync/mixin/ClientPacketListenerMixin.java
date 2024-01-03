package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.PacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.ducks.INetworkAddon;
import net.earthcomputer.minimapsync.model.Model;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IHasModel, INetworkAddon {
    @Unique
    private Model model = new Model();
    @Unique
    private int protocolVersion;
    @Unique
    private final PacketSplitter packetSplitter = new PacketSplitter.Client((ClientPacketListener) (Object) this);

    @Override
    public Model minimapsync_model() {
        return model;
    }

    @Override
    public void minimapsync_setModel(Model model) {
        this.model = model;
    }

    @Override
    public int minimapsync_getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public PacketSplitter minimapsync_getPacketSplitter() {
        return packetSplitter;
    }
}
