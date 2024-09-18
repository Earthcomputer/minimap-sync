package net.earthcomputer.minimapsync.network;

import net.earthcomputer.minimapsync.MinimapSync;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import java.util.function.Consumer;

public final class ProtocolVersionTask implements ConfigurationTask {
    public static final Type TYPE = new Type("minimapsync:protocol_version");
    public static final ProtocolVersionTask INSTANCE = new ProtocolVersionTask();

    private ProtocolVersionTask() {
    }

    @Override
    public void start(Consumer<Packet<?>> packetSender) {
        packetSender.accept(ServerConfigurationNetworking.createS2CPacket(new ProtocolVersionPayload(MinimapSync.CURRENT_PROTOCOL_VERSION)));
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
