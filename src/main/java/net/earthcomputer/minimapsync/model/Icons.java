package net.earthcomputer.minimapsync.model;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

public record Icons(Map<String, byte[]> icons, Set<String> dirty) {
    public static final StreamCodec<ByteBuf, Icons> STREAM_CODEC = ByteBufCodecs.map(
        (IntFunction<Map<String, byte[]>>) HashMap::new,
        ByteBufCodecs.STRING_UTF8,
        ByteBufCodecs.BYTE_ARRAY
    ).map(icons -> new Icons(icons, new HashSet<>()), Icons::icons);

    public Icons() {
        this(new HashMap<>(), new HashSet<>());
    }

    public byte @Nullable [] get(String name) {
        return icons.get(name);
    }

    public void put(String name, byte[] icon) {
        dirty.add(name);
        icons.put(name, icon);
    }

    public byte @Nullable [] remove(String name) {
        dirty.add(name);
        return icons.remove(name);
    }

    public void forEach(BiConsumer<? super String, ? super byte[]> consumer) {
        icons.forEach(consumer);
    }

    public Set<String> names() {
        return icons.keySet();
    }

    public int size() {
        return icons.size();
    }

    public boolean isEmpty() {
        return icons.isEmpty();
    }
}
