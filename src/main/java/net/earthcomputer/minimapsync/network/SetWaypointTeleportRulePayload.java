package net.earthcomputer.minimapsync.network;

import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetWaypointTeleportRulePayload(WaypointTeleportRule rule) implements CustomPacketPayload {
    public static final Type<SetWaypointTeleportRulePayload> TYPE = new Type<>(new ResourceLocation("minimapsync", "set_waypoint_teleport_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWaypointTeleportRulePayload> CODEC = WaypointTeleportRule.STREAM_CODEC
        .<RegistryFriendlyByteBuf>cast()
        .map(SetWaypointTeleportRulePayload::new, SetWaypointTeleportRulePayload::rule);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
