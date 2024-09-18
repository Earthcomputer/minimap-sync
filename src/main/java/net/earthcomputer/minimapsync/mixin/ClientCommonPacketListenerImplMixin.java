package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin implements IHasProtocolVersion {
    @Unique
    private int protocolVersion;

    @Override
    public int minimapsync_getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
}
