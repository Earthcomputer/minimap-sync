package net.earthcomputer.minimapsync;

import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Utilities, mostly because some functions are slightly different between Minecraft versions, but
 * we want to be cross-version compatible
 */
public final class FriendlyByteBufUtil {
    private FriendlyByteBufUtil() {}

    public static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> deserializer) {
        return readCollection(buf, ArrayList::new, deserializer);
    }

    public static <T, C extends Collection<T>> C readCollection(FriendlyByteBuf buf, IntFunction<C> collectionSupplier, Function<FriendlyByteBuf, T> deserializer) {
        int size = buf.readVarInt();
        C result = collectionSupplier.apply(size);
        for (int i = 0; i < size; i++) {
            result.add(deserializer.apply(buf));
        }
        return result;
    }

    public static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> collection, BiConsumer<FriendlyByteBuf, T> serializer) {
        buf.writeVarInt(collection.size());
        for (T element : collection) {
            serializer.accept(buf, element);
        }
    }

    @Nullable
    public static <T> T readNullable(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> deserializer) {
        return buf.readBoolean() ? deserializer.apply(buf) : null;
    }

    public static <T> void writeNullable(FriendlyByteBuf buf, @Nullable T value, BiConsumer<FriendlyByteBuf, T> serializer) {
        buf.writeBoolean(value != null);
        if (value != null) {
            serializer.accept(buf, value);
        }
    }

    public static <T> ResourceKey<T> readResourceKey(FriendlyByteBuf buf, ResourceKey<? extends Registry<T>> key) {
        return ResourceKey.create(key, buf.readResourceLocation());
    }

    public static void writeResourceKey(FriendlyByteBuf buf, ResourceKey<?> key) {
        buf.writeResourceLocation(key.location());
    }
}
