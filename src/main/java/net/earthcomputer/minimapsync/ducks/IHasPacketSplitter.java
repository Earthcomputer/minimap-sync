package net.earthcomputer.minimapsync.ducks;

import net.earthcomputer.minimapsync.network.PacketSplitter;

public interface IHasPacketSplitter<C> {
    PacketSplitter<C> minimapsync_getPacketSplitter();
    void minimapsync_setPacketSplitter(PacketSplitter<C> packetSplitter);
}
