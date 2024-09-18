package net.earthcomputer.minimapsync.mixin;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitterSendableChannels;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Set;
import java.util.function.Function;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin implements IHasProtocolVersion, IHasPacketSplitterSendableChannels {
    @Unique
    private int minimapsync_protocolVersion;
    @Unique
    private Set<ResourceLocation> packetSplitterSendableChannels = Set.of();

    @Override
    public int minimapsync_getProtocolVersion() {
        return minimapsync_protocolVersion;
    }

    @Override
    public void minimapsync_setProtocolVersion(int protocolVersion) {
        minimapsync_protocolVersion = protocolVersion;
    }

    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private Function<ByteBuf, RegistryFriendlyByteBuf> addMetadataToBufDecorator(Function<ByteBuf, RegistryFriendlyByteBuf> decorator) {
        int protocol = minimapsync_protocolVersion;
        return decorator.andThen(buf -> {
            ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(protocol);
            return buf;
        });
    }

    @SuppressWarnings("unchecked")
    @ModifyVariable(method = "handleConfigurationFinished", at = @At("STORE"))
    private ServerPlayer addMetadataToPlayer(ServerPlayer player) {
        ((IHasProtocolVersion) player).minimapsync_setProtocolVersion(minimapsync_protocolVersion);
        ((IHasPacketSplitter<ServerPlayNetworking.Context>) player).minimapsync_setPacketSplitter(new PacketSplitter.Server(packetSplitterSendableChannels, player));
        return player;
    }

    @Override
    public void minimapsync_setPacketSplitterSendableChannels(Set<ResourceLocation> packetSplitterSendableChannels) {
        this.packetSplitterSendableChannels = packetSplitterSendableChannels;
    }
}
