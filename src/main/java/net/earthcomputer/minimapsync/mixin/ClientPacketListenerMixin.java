package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.model.Model;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IHasPacketSplitter<ClientPlayNetworking.Context>, IHasModel {
    @Unique
    private Model model = new Model();
    @Unique
    private PacketSplitter<ClientPlayNetworking.Context> packetSplitter;

    @Override
    public Model minimapsync_model() {
        return model;
    }

    @Override
    public void minimapsync_setModel(Model model) {
        this.model = model;
    }

    @Override
    public PacketSplitter<ClientPlayNetworking.Context> minimapsync_getPacketSplitter() {
        return packetSplitter;
    }

    @Override
    public void minimapsync_setPacketSplitter(PacketSplitter<ClientPlayNetworking.Context> packetSplitter) {
        this.packetSplitter = packetSplitter;
    }
}
