package net.earthcomputer.minimapsync.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import java.util.function.Consumer;

public final class PacketSplitterRegisterChannelsTask implements ConfigurationTask {
    public static final Type TYPE = new Type("minimapsync:packet_splitter_register_channels");
    public static final PacketSplitterRegisterChannelsTask INSTANCE = new PacketSplitterRegisterChannelsTask();

    private PacketSplitterRegisterChannelsTask() {
    }

    @Override
    public void start(Consumer<Packet<?>> packetSender) {
        PacketSplitter.sendServerboundSendable(packetSender);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
