package net.earthcomputer.minimapsync.ducks;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public interface IHasPacketSplitterSendableChannels {
    void minimapsync_setPacketSplitterSendableChannels(Set<ResourceLocation> packetSplitterSendableChannels);
}
