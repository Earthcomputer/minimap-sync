package net.earthcomputer.minimapsync.mixin;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.PacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationPacketListenerImplMixin {
    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private Function<ByteBuf, RegistryFriendlyByteBuf> addProtocolToBufDecorator(Function<ByteBuf, RegistryFriendlyByteBuf> decorator) {
        int protocolVersion = ((IHasProtocolVersion) this).minimapsync_getProtocolVersion();
        return decorator.andThen(buf -> {
            ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(protocolVersion);
            return buf;
        });
    }

    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"))
    private PacketListener addProtocolToGamePacketListener(PacketListener packetListener) {
        ((IHasProtocolVersion) packetListener).minimapsync_setProtocolVersion(((IHasProtocolVersion) this).minimapsync_getProtocolVersion());
        return packetListener;
    }
}
