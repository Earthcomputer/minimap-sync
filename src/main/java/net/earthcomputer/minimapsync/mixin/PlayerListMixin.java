package net.earthcomputer.minimapsync.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @ModifyArg(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private Function<ByteBuf, RegistryFriendlyByteBuf> addProtocolToBufDecorator(Function<ByteBuf, RegistryFriendlyByteBuf> decorator, @Local(argsOnly = true) ServerPlayer player) {
        int protocol = ((IHasProtocolVersion) player).minimapsync_getProtocolVersion();
        return decorator.andThen(buf -> {
            ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(protocol);
            return buf;
        });
    }
}
