package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.PacketSplitter;
import net.earthcomputer.minimapsync.ducks.INetworkAddon;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin implements INetworkAddon {
    @Unique
    private int minimapsync_protocolVersion;
    @Unique
    private final PacketSplitter packetSplitter = new PacketSplitter.Server((ServerGamePacketListenerImpl) (Object) this);

    @Override
    public int minimapsync_getProtocolVersion() {
        return minimapsync_protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        minimapsync_protocolVersion = protocolVersion;
    }

    @Override
    public PacketSplitter minimapsync_getPacketSplitter() {
        return packetSplitter;
    }
}
