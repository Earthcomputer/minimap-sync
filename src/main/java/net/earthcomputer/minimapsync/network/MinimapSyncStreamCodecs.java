package net.earthcomputer.minimapsync.network;

import com.mojang.datafixers.util.Function11;
import io.netty.buffer.ByteBuf;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MinimapSyncStreamCodecs {
    private MinimapSyncStreamCodecs() {
    }

    public static int getProtocolVersion(RegistryFriendlyByteBuf buf) {
        return ((IHasProtocolVersion) buf).minimapsync_getProtocolVersion();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Integer> PROTOCOL_VERSION = new StreamCodec<>() {
        @Override
        public Integer decode(RegistryFriendlyByteBuf buf) {
            return getProtocolVersion(buf);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Integer value) {
            if (value != getProtocolVersion(buf)) {
                throw new IllegalStateException("Can't encode '" + value + "', expected '" + getProtocolVersion(buf) + "'");
            }
        }
    };

    public static final StreamCodec<ByteBuf, Long> LONG = new StreamCodec<>() {
        @Override
        public Long decode(ByteBuf buf) {
            return buf.readLong();
        }

        @Override
        public void encode(ByteBuf buf, Long value) {
            buf.writeLong(value);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, UUID> UUID = new StreamCodec<>() {
        @Override
        public UUID decode(FriendlyByteBuf buf) {
            return buf.readUUID();
        }

        @Override
        public void encode(FriendlyByteBuf buf, UUID value) {
            buf.writeUUID(value);
        }
    };

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> protocolSelect(int protocolVersion, StreamCodec<? super RegistryFriendlyByteBuf, T> sinceVersion, StreamCodec<? super RegistryFriendlyByteBuf, T> beforeVersion) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                return getProtocolVersion(buf) >= protocolVersion ? sinceVersion.decode(buf) : beforeVersion.decode(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                if (getProtocolVersion(buf) >= protocolVersion) {
                    sinceVersion.encode(buf, value);
                } else {
                    beforeVersion.encode(buf, value);
                }
            }
        };
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> defaultedBeforeProtocol(int protocolVersion, StreamCodec<? super RegistryFriendlyByteBuf, T> sinceVersion, Supplier<T> beforeVersion) {
        return protocolSelect(protocolVersion, sinceVersion, unit(beforeVersion));
    }

    public static <B, T> StreamCodec<B, T> unit(Supplier<T> supplier) {
        return new StreamCodec<>() {
            @Override
            public T decode(B buf) {
                return supplier.get();
            }

            @Override
            public void encode(B buf, T value) {
                T expectedValue = supplier.get();
                if (!value.equals(expectedValue)) {
                    throw new IllegalStateException("Can't encode '" + value + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    public static <B extends ByteBuf, T> StreamCodec<B, @Nullable T> nullable(StreamCodec<B, T> codec) {
        return ByteBufCodecs.optional(codec).map(opt -> opt.orElse(null), Optional::ofNullable);
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> getter1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> getter2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> getter3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> getter4,
        StreamCodec<? super B, T5> codec5,
        Function<C, T5> getter5,
        StreamCodec<? super B, T6> codec6,
        Function<C, T6> getter6,
        StreamCodec<? super B, T7> codec7,
        Function<C, T7> getter7,
        StreamCodec<? super B, T8> codec8,
        Function<C, T8> getter8,
        StreamCodec<? super B, T9> codec9,
        Function<C, T9> getter9,
        StreamCodec<? super B, T10> codec10,
        Function<C, T10> getter10,
        StreamCodec<? super B, T11> codec11,
        Function<C, T11> getter11,
        Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, C> factory
    ) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 object1 = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                T3 object3 = codec3.decode(buf);
                T4 object4 = codec4.decode(buf);
                T5 object5 = codec5.decode(buf);
                T6 object6 = codec6.decode(buf);
                T7 object7 = codec7.decode(buf);
                T8 object8 = codec8.decode(buf);
                T9 object9 = codec9.decode(buf);
                T10 object10 = codec10.decode(buf);
                T11 object11 = codec11.decode(buf);
                return factory.apply(object1, object2, object3, object4, object5, object6, object7, object8, object9, object10, object11);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, getter1.apply(value));
                codec2.encode(buf, getter2.apply(value));
                codec3.encode(buf, getter3.apply(value));
                codec4.encode(buf, getter4.apply(value));
                codec5.encode(buf, getter5.apply(value));
                codec6.encode(buf, getter6.apply(value));
                codec7.encode(buf, getter7.apply(value));
                codec8.encode(buf, getter8.apply(value));
                codec9.encode(buf, getter9.apply(value));
                codec10.encode(buf, getter10.apply(value));
                codec11.encode(buf, getter11.apply(value));
            }
        };
    }
}
