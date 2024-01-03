package net.earthcomputer.minimapsync.ducks;

import net.earthcomputer.minimapsync.PacketSplitter;

public interface INetworkAddon {
    int minimapsync_getProtocolVersion();
    void minimapsync_setProtocolVersion(int protocolVersion);
    PacketSplitter minimapsync_getPacketSplitter();
}
