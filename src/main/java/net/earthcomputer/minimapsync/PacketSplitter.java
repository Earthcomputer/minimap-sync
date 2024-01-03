package net.earthcomputer.minimapsync;

import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.ducks.INetworkAddon;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public abstract sealed class PacketSplitter {
    private int waitingToReceive = 0;
    private FriendlyByteBuf receiving;

    protected abstract int getLimit();
    protected abstract int getProtocolVersion();
    protected abstract void send0(ResourceLocation channel, FriendlyByteBuf buf);

    public void send(ResourceLocation channel, FriendlyByteBuf buf) {
        if (getProtocolVersion() < 4) {
            // old protocol versions do not support packet splitting
            send0(channel, buf);
            return;
        }

        int bytesToSend = buf.readableBytes() + 5;
        int numPackets = Mth.positiveCeilDiv(bytesToSend, getLimit());

        FriendlyByteBuf splitBuf = PacketByteBufs.create();
        splitBuf.writeVarInt(numPackets);
        splitBuf.writeBytes(buf, Math.min(buf.readableBytes(), getLimit() - 5));
        send0(channel, splitBuf);
        bytesToSend -= getLimit();

        while (bytesToSend > 0) {
            splitBuf = PacketByteBufs.create();
            splitBuf.writeBytes(buf, Math.min(buf.readableBytes(), getLimit()));
            send0(channel, splitBuf);
            bytesToSend -= getLimit();
        }
    }

    public void receive(FriendlyByteBuf buf, Consumer<FriendlyByteBuf> handler) {
        if (getProtocolVersion() < 4) {
            // old protocol versions do not support packet splitting
            handler.accept(buf);
            return;
        }

        if (waitingToReceive == 0) {
            waitingToReceive = buf.readVarInt();
            receiving = PacketByteBufs.create();
        }
        receiving.writeBytes(buf);
        if (--waitingToReceive == 0) {
            handler.accept(receiving);
            receiving = null;
        }
    }

    public static PacketSplitter get(ClientPacketListener connection) {
        return ((INetworkAddon) connection).minimapsync_getPacketSplitter();
    }

    public static PacketSplitter get(ServerGamePacketListenerImpl connection) {
        return ((INetworkAddon) connection).minimapsync_getPacketSplitter();
    }

    public static final class Client extends PacketSplitter {
        private final ClientPacketListener connection;

        public Client(ClientPacketListener connection) {
            this.connection = connection;
        }

        @Override
        protected int getLimit() {
            return 32767;
        }

        @Override
        protected int getProtocolVersion() {
            return MinimapSyncClient.getProtocolVersion(connection);
        }

        @Override
        protected void send0(ResourceLocation channel, FriendlyByteBuf buf) {
            connection.send(ClientPlayNetworking.createC2SPacket(channel, buf));
        }
    }

    public static final class Server extends PacketSplitter {
        private final ServerGamePacketListenerImpl connection;

        public Server(ServerGamePacketListenerImpl connection) {
            this.connection = connection;
        }

        @Override
        protected int getLimit() {
            return 1048576;
        }

        @Override
        protected int getProtocolVersion() {
            return MinimapSync.getProtocolVersion(connection);
        }

        @Override
        protected void send0(ResourceLocation channel, FriendlyByteBuf buf) {
            connection.send(ServerPlayNetworking.createS2CPacket(channel, buf));
        }
    }
}
