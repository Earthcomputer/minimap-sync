package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements IHasProtocolVersion {
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
