package net.earthcomputer.minimapsync.mixin;

import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements IHasProtocolVersion, IHasPacketSplitter<ServerPlayNetworking.Context> {
    @Unique
    private int minimapsync_protocolVersion;
    @Unique
    private PacketSplitter<ServerPlayNetworking.Context> packetSplitter;

    @Override
    public int minimapsync_getProtocolVersion() {
        return minimapsync_protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        minimapsync_protocolVersion = protocolVersion;
    }

    @Override
    public PacketSplitter<ServerPlayNetworking.Context> minimapsync_getPacketSplitter() {
        return packetSplitter;
    }

    @Override
    public void minimapsync_setPacketSplitter(PacketSplitter<ServerPlayNetworking.Context> packetSplitter) {
        this.packetSplitter = packetSplitter;
    }
}
