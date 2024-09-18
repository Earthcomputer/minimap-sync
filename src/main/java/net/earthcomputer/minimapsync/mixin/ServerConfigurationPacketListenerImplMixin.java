package net.earthcomputer.minimapsync.mixin;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Function;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin implements IHasProtocolVersion {
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

    @ModifyArg(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private Function<ByteBuf, RegistryFriendlyByteBuf> addProtocolToBufDecorator(Function<ByteBuf, RegistryFriendlyByteBuf> decorator) {
        int protocol = minimapsync_protocolVersion;
        return decorator.andThen(buf -> {
            ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(protocol);
            return buf;
        });
    }

    @ModifyVariable(method = "handleConfigurationFinished", at = @At("STORE"))
    private ServerPlayer addProtocolToPlayer(ServerPlayer player) {
        ((IHasProtocolVersion) player).minimapsync_setProtocolVersion(minimapsync_protocolVersion);
        return player;
    }
}
