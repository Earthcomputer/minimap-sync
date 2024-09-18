package net.earthcomputer.minimapsync.network;

import io.netty.buffer.Unpooled;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.client.MinimapSyncClient;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.Optionull;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract sealed class PacketSplitter<C> {
    private static final Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, ?>> PACKET_TYPES = new HashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<? extends CustomPacketPayload, ClientPlayNetworking.Context>> CLIENTBOUND_HANDLERS = new HashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<? extends CustomPacketPayload, ServerPlayNetworking.Context>> SERVERBOUND_HANDLERS = new HashMap<>();

    public static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        PACKET_TYPES.put(type.id(), new CustomPacketPayload.TypeAndCodec<>(type, codec));
    }

    public static <T extends CustomPacketPayload> void registerClientboundHandler(CustomPacketPayload.Type<T> type, BiConsumer<T, ClientPlayNetworking.Context> handler) {
        CLIENTBOUND_HANDLERS.put(type, handler);
    }

    public static <T extends CustomPacketPayload> void registerServerboundHandler(CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayNetworking.Context> handler) {
        SERVERBOUND_HANDLERS.put(type, handler);
    }

    @Nullable
    public static CustomPacketPayload.Type<?> getType(ResourceLocation id) {
        return Optionull.map(PACKET_TYPES.get(id), CustomPacketPayload.TypeAndCodec::type);
    }

    private CustomPacketPayload.Type<?> receivingType;
    private RegistryFriendlyByteBuf receiving;

    protected abstract int getLimit();
    protected abstract int getProtocolVersion();
    protected abstract RegistryAccess getRegistryAccess();
    protected abstract void send0(SplitPacketPayload splitPayload);
    protected abstract <T extends CustomPacketPayload> void receive0(T payload, C context);

    public final void send(CustomPacketPayload payload) {
        RegistryFriendlyByteBuf buf = encode(payload);

        int bytesPerPacket = getLimit() - (
            5 // payload length
            + 1 // isLastPayload
            + 1 // colon in identifier
            + 5 // identifier length
            + payload.type().id().getNamespace().length()
            + payload.type().id().getPath().length()
        );
        while (buf.readableBytes() > bytesPerPacket) {
            byte[] data = new byte[bytesPerPacket];
            buf.readBytes(data);
            send0(new SplitPacketPayload(payload.type(), false, data));
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        send0(new SplitPacketPayload(payload.type(), true, data));
    }

    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> RegistryFriendlyByteBuf encode(T payload) {
        CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, T> typeAndCodec = (CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, T>) PACKET_TYPES.get(payload.type().id());
        if (typeAndCodec == null) {
            throw new IllegalStateException("Cannot send packet type " + payload.type().id() + " via packet splitter");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), getRegistryAccess());
        ((IHasProtocolVersion) buf).minimapsync_setProtocolVersion(getProtocolVersion());
        typeAndCodec.codec().encode(buf, payload);
        return buf;
    }

    public final void receive(SplitPacketPayload splitPayload, C context) {
        if (receivingType != null && receivingType != splitPayload.innerType()) {
            receiving.release();
            receivingType = null;
            receiving = null;
        }

        if (receivingType == null) {
            receivingType = splitPayload.innerType();
            if (!PACKET_TYPES.containsKey(receivingType.id())) {
                throw new IllegalStateException("Cannot receive packet type " + receivingType.id() + " via packet splitter");
            }
            receiving = new RegistryFriendlyByteBuf(Unpooled.buffer(), getRegistryAccess());
            ((IHasProtocolVersion) receiving).minimapsync_setProtocolVersion(getProtocolVersion());
        }

        receiving.writeBytes(splitPayload.data());

        if (splitPayload.isLastPayload()) {
            try {
                CustomPacketPayload payload = PACKET_TYPES.get(receivingType.id()).codec().decode(receiving);
                receive0(payload, context);
            } finally {
                receiving.release();
                receivingType = null;
                receiving = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static PacketSplitter<ClientPlayNetworking.Context> get(ClientPacketListener connection) {
        return ((IHasPacketSplitter<ClientPlayNetworking.Context>) connection).minimapsync_getPacketSplitter();
    }

    @SuppressWarnings("unchecked")
    public static PacketSplitter<ServerPlayNetworking.Context> get(ServerGamePacketListenerImpl connection) {
        return ((IHasPacketSplitter<ServerPlayNetworking.Context>) connection).minimapsync_getPacketSplitter();
    }

    public static final class Client extends PacketSplitter<ClientPlayNetworking.Context> {
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
        protected RegistryAccess getRegistryAccess() {
            return connection.registryAccess();
        }

        @Override
        protected void send0(SplitPacketPayload splitPayload) {
            connection.send(ClientPlayNetworking.createC2SPacket(splitPayload));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <T extends CustomPacketPayload> void receive0(T payload, ClientPlayNetworking.Context context) {
            var handler = (BiConsumer<T, ClientPlayNetworking.Context>) CLIENTBOUND_HANDLERS.get(payload.type());
            if (handler == null) {
                throw new IllegalStateException("Cannot receive packet type " + payload.type() + " on client via packet splitter");
            }
            handler.accept(payload, context);
        }
    }

    public static final class Server extends PacketSplitter<ServerPlayNetworking.Context> {
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
        protected RegistryAccess getRegistryAccess() {
            return connection.player.registryAccess();
        }

        @Override
        protected void send0(SplitPacketPayload splitPayload) {
            connection.send(ServerPlayNetworking.createS2CPacket(splitPayload));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <T extends CustomPacketPayload> void receive0(T payload, ServerPlayNetworking.Context context) {
            var handler = (BiConsumer<T, ServerPlayNetworking.Context>) SERVERBOUND_HANDLERS.get(payload.type());
            if (handler == null) {
                throw new IllegalStateException("Cannot receive packet type " + payload.type() + " on server via packet splitter");
            }
            handler.accept(payload, context);
        }
    }
}
