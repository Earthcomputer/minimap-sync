package net.earthcomputer.minimapsync.model;

import net.earthcomputer.minimapsync.FriendlyByteBufUtil;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public record Icons(Map<String, byte[]> icons, Set<String> dirty) {
    public Icons(FriendlyByteBuf buf) {
        this(
            FriendlyByteBufUtil.readMap(buf, HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readByteArray),
            new HashSet<>()
        );
    }

    public Icons() {
        this(new HashMap<>(), new HashSet<>());
    }

    public void toPacket(FriendlyByteBuf buf) {
        FriendlyByteBufUtil.writeMap(buf, icons, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeByteArray);
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
