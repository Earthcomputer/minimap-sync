package net.earthcomputer.minimapsync.mixin;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitterSendableChannels;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Set;
import java.util.function.Function;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationPacketListenerImplMixin implements IHasPacketSplitterSendableChannels {
    @Unique
    private Set<ResourceLocation> packetSplitterSendableChannels = Set.of();

    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private Function<ByteBuf, RegistryFriendlyByteBuf> addMetadataToDecorator(Function<ByteBuf, RegistryFriendlyByteBuf> decorator) {
        int protocolVersion = ((IHasProtocolVersion) this).minimapsync_getProtocolVersion();
        return decorator.andThen(buf -> {
            ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(protocolVersion);
            return buf;
        });
    }

    @SuppressWarnings("unchecked")
    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"))
    private PacketListener addMetadataToGamePacketListener(PacketListener packetListener) {
        ((IHasProtocolVersion) packetListener).minimapsync_setProtocolVersion(((IHasProtocolVersion) this).minimapsync_getProtocolVersion());
        ((IHasPacketSplitter<ClientPlayNetworking.Context>) packetListener).minimapsync_setPacketSplitter(new PacketSplitter.Client(packetSplitterSendableChannels, (ClientPacketListener) packetListener));
        return packetListener;
    }

    @Override
    public void minimapsync_setPacketSplitterSendableChannels(Set<ResourceLocation> packetSplitterSendableChannels) {
        this.packetSplitterSendableChannels = packetSplitterSendableChannels;
    }
}
