package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin implements IHasProtocolVersion {
    @Unique
    private int minimapsync_protocolVersion;

    @Override
    public int minimapsync_getProtocolVersion() {
        return minimapsync_protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        minimapsync_protocolVersion = protocolVersion;
    }
}
