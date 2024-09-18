package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin implements IHasProtocolVersion, IHasPacketSplitter<ServerPlayNetworking.Context> {
    @Shadow
    public ServerPlayer player;

    @Override
    public int minimapsync_getProtocolVersion() {
        return ((IHasProtocolVersion) this.player).minimapsync_getProtocolVersion();
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        ((IHasProtocolVersion) this.player).minimapsync_setProtocolVersion(protocolVersion);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketSplitter<ServerPlayNetworking.Context> minimapsync_getPacketSplitter() {
        return ((IHasPacketSplitter<ServerPlayNetworking.Context>) this.player).minimapsync_getPacketSplitter();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void minimapsync_setPacketSplitter(PacketSplitter<ServerPlayNetworking.Context> packetSplitter) {
        ((IHasPacketSplitter<ServerPlayNetworking.Context>) this.player).minimapsync_setPacketSplitter(packetSplitter);
    }
}
