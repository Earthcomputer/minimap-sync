package net.earthcomputer.minimapsync.client;

import com.google.common.collect.ImmutableList;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitterSendableChannels;
import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.earthcomputer.minimapsync.network.AddIconPayload;
import net.earthcomputer.minimapsync.network.AddWaypointPayload;
import net.earthcomputer.minimapsync.network.InitModelPayload;
import net.earthcomputer.minimapsync.network.PacketSplitterRegisterChannelsPayload;
import net.earthcomputer.minimapsync.network.ProtocolVersionPayload;
import net.earthcomputer.minimapsync.network.RemoveIconPayload;
import net.earthcomputer.minimapsync.network.RemoveWaypointPayload;
import net.earthcomputer.minimapsync.network.SetWaypointColorPayload;
import net.earthcomputer.minimapsync.network.SetWaypointDescriptionPayload;
import net.earthcomputer.minimapsync.network.SetWaypointDimensionsPayload;
import net.earthcomputer.minimapsync.network.SetWaypointIconPayload;
import net.earthcomputer.minimapsync.network.SetWaypointPosPayload;
import net.earthcomputer.minimapsync.network.SetWaypointTeleportRulePayload;
import net.earthcomputer.minimapsync.network.SplitPacketPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class MinimapSyncClient implements ClientModInitializer {
    @Nullable
    private static IMinimapCompat currentIgnore;

    private static boolean hasRunReadyTasks = false;
    private static final List<Runnable> whenReady = new ArrayList<>();

    // wrap in inner class to lazily compute this value
    private static class CompatsHolder {
        private static final List<IMinimapCompat> COMPATS = Util.make(() -> {
            FabricLoader loader = FabricLoader.getInstance();
            var builder = ImmutableList.<IMinimapCompat>builder();
            if (loader.isModLoaded("voxelmap")) {
                builder.add(VoxelMapCompat.INSTANCE.init());
            }
            if (loader.isModLoaded("journeymap-api-fabric")) {
                builder.add(JourneyMapCompat.instance());
            }
            if (loader.isModLoaded("xaerominimap")) {
                builder.add(XaerosMapCompat.INSTANCE.init());
            }
            return builder.build();
        });
    }

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (getProtocolVersion(handler) < MinimapSync.MINIMUM_PROTOCOL_VERSION && ClientPlayNetworking.canSend(AddWaypointPayload.TYPE)) {
                handler.getConnection().disconnect(MinimapSync.translatableWithFallback("minimapsync.disconnect.server_outdated", getProtocolVersion(handler), MinimapSync.MINIMUM_PROTOCOL_VERSION));
            }
            checkReady();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            hasRunReadyTasks = false;
            whenReady.clear();
        });

        ClientConfigurationNetworking.registerGlobalReceiver(ProtocolVersionPayload.TYPE, (payload, context) -> {
            if (payload.protocolVersion() < MinimapSync.MINIMUM_PROTOCOL_VERSION) {
                context.responseSender().disconnect(MinimapSync.translatableWithFallback("minimapsync.disconnect.server_outdated", payload.protocolVersion(), MinimapSync.MINIMUM_PROTOCOL_VERSION));
                return;
            }

            ((IHasProtocolVersion) getPacketListener(context)).minimapsync_setProtocolVersion(Math.min(MinimapSync.CURRENT_PROTOCOL_VERSION, payload.protocolVersion()));
            context.responseSender().sendPacket(new ProtocolVersionPayload(MinimapSync.CURRENT_PROTOCOL_VERSION));
        });
        ClientConfigurationNetworking.registerGlobalReceiver(PacketSplitterRegisterChannelsPayload.TYPE, (payload, context) -> {
            ((IHasPacketSplitterSendableChannels) getPacketListener(context)).minimapsync_setPacketSplitterSendableChannels(payload.channels());
            PacketSplitter.sendClientboundSendable();
        });
        ClientPlayNetworking.registerGlobalReceiver(SplitPacketPayload.TYPE, (payload, context) -> {
            var packetSplitter = PacketSplitter.get(context.player().connection);
            if (packetSplitter != null) {
                packetSplitter.receive(payload, context);
            }
        });
        PacketSplitter.registerClientboundHandler(InitModelPayload.TYPE, (payload, context) -> {
            whenReady(() -> initModel(context.player().connection, payload.model()));
        });
        ClientPlayNetworking.registerGlobalReceiver(AddWaypointPayload.TYPE, (payload, context) -> {
            whenReady(() -> addWaypoint(null, context.player().connection, payload.waypoint()));
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoveWaypointPayload.TYPE, (payload, context) -> {
            whenReady(() -> removeWaypoint(null, context.player().connection, payload.name()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointDimensionsPayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointDimensions(null, context.player().connection, payload.name(), payload.dimensions()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointPosPayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointPos(null, context.player().connection, payload.name(), payload.pos()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointColorPayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointColor(null, context.player().connection, payload.name(), payload.color()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointDescriptionPayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointDescription(null, context.player().connection, payload.name(), payload.description()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointTeleportRulePayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointTeleportRule(null, context.player().connection, payload.rule()));
        });
        PacketSplitter.registerClientboundHandler(AddIconPayload.TYPE, (payload, context) -> {
            whenReady(() -> addIcon(null, context.player().connection, payload.name(), payload.icon()));
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoveIconPayload.TYPE, (payload, context) -> {
            whenReady(() -> removeIcon(null, context.player().connection, payload.name()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetWaypointIconPayload.TYPE, (payload, context) -> {
            whenReady(() -> setWaypointIcon(null, context.player().connection, payload.waypoint(), payload.icon()));
        });
    }

    private static final VarHandle HANDLER_FIELD = Util.make(() -> {
        try {
            Class<?> clientCommonNetworkAddonClass = Class.forName("net.fabricmc.fabric.impl.networking.client.ClientCommonNetworkAddon");
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clientCommonNetworkAddonClass, MethodHandles.lookup());
            return lookup.findVarHandle(clientCommonNetworkAddonClass, "handler", ClientCommonPacketListenerImpl.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    });
    private static ClientConfigurationPacketListenerImpl getPacketListener(ClientConfigurationNetworking.Context context) {
        return (ClientConfigurationPacketListenerImpl) HANDLER_FIELD.get(context.responseSender());
    }

    private static boolean isReady() {
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (!compat.isReady()) {
                return false;
            }
        }
        return true;
    }

    private static void whenReady(Runnable runnable) {
        if (isReady()) {
            runnable.run();
        } else {
            whenReady.add(runnable);
        }
    }

    public static void checkReady() {
        if (hasRunReadyTasks || !isReady()) {
            return;
        }
        hasRunReadyTasks = true;

        for (Runnable runnable : whenReady) {
            runnable.run();
        }
        whenReady.clear();
    }

    public static int getProtocolVersion(ClientCommonPacketListenerImpl handler) {
        return ((IHasProtocolVersion) handler).minimapsync_getProtocolVersion();
    }

    public static boolean isCompatibleServer() {
        return ClientPlayNetworking.canSend(AddWaypointPayload.TYPE);
    }

    private static void initModel(ClientPacketListener handler, Model model) {
        Model.set(handler, model);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            IMinimapCompat prevIgnore = currentIgnore;
            try {
                currentIgnore = compat;
                compat.initModel(handler, model);
            } finally {
                currentIgnore = prevIgnore;
            }
        }
    }

    private static boolean addWaypoint(@Nullable IMinimapCompat source, ClientPacketListener handler, Waypoint waypoint) {
        Model model = Model.get(handler);
        if (!model.waypoints().addWaypoint(waypoint)) {
            return false;
        }
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.addWaypoint(handler, waypoint);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
        return true;
    }

    private static void removeWaypoint(@Nullable IMinimapCompat source, ClientPacketListener handler, String name) {
        Model model = Model.get(handler);
        model.waypoints().removeWaypoint(null, name);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.removeWaypoint(handler, name);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointDimensions(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        Model model = Model.get(handler);
        model.waypoints().setWaypointDimensions(null, name, dimensions);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointDimensions(handler, name, dimensions);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointPos(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, BlockPos pos) {
        Model model = Model.get(handler);
        model.waypoints().setPos(null, name, pos);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointPos(handler, name, pos);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointColor(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, int color) {
        Model model = Model.get(handler);
        model.waypoints().setColor(null, name, color);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointColor(handler, name, color);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointDescription(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, @Nullable String description) {
        Model model = Model.get(handler);
        model.waypoints().setDescription(null, name, description);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointDescription(handler, name, description);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointTeleportRule(@Nullable IMinimapCompat source, ClientPacketListener handler, WaypointTeleportRule rule) {
        Model model = Model.get(handler).withTeleportRule(rule);
        Model.set(handler, model);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointTeleportRule(handler, rule);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void addIcon(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, byte[] icon) {
        Model.get(handler).icons().put(name, icon);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.addIcon(handler, name, icon);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void removeIcon(@Nullable IMinimapCompat source, ClientPacketListener handler, String name) {
        Model.get(handler).icons().remove(name);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.removeIcon(handler, name);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    private static void setWaypointIcon(@Nullable IMinimapCompat source, ClientPacketListener handler, String waypoint, @Nullable String icon) {
        Model.get(handler).waypoints().setIcon(null, waypoint, icon);
        for (IMinimapCompat compat : CompatsHolder.COMPATS) {
            if (compat != source) {
                IMinimapCompat prevIgnore = currentIgnore;
                try {
                    currentIgnore = compat;
                    compat.setWaypointIcon(handler, waypoint, icon);
                } finally {
                    currentIgnore = prevIgnore;
                }
            }
        }
    }

    public static void onAddWaypoint(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (!addWaypoint(source, connection, waypoint)) {
            return;
        }

        if (ClientPlayNetworking.canSend(AddWaypointPayload.TYPE)) {
            ClientPlayNetworking.send(new AddWaypointPayload(waypoint));
        }
    }

    public static void onRemoveWaypoint(IMinimapCompat source, String name) {
        if (source == currentIgnore) {
            return;
        }

        removeWaypoint(source, Minecraft.getInstance().getConnection(), name);

        if (ClientPlayNetworking.canSend(RemoveWaypointPayload.TYPE)) {
            ClientPlayNetworking.send(new RemoveWaypointPayload(name));
        }
    }

    public static void onSetWaypointDimensions(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointDimensions(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.dimensions());

        if (ClientPlayNetworking.canSend(SetWaypointDimensionsPayload.TYPE)) {
            ClientPlayNetworking.send(new SetWaypointDimensionsPayload(waypoint.name(), waypoint.dimensions()));
        }
    }

    public static void onSetWaypointPos(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointPos(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.pos());

        if (ClientPlayNetworking.canSend(SetWaypointPosPayload.TYPE)) {
            ClientPlayNetworking.send(new SetWaypointPosPayload(waypoint.name(), waypoint.pos()));
        }
    }

    public static void onSetWaypointColor(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointColor(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.color());

        if (ClientPlayNetworking.canSend(SetWaypointColorPayload.TYPE)) {
            ClientPlayNetworking.send(new SetWaypointColorPayload(waypoint.name(), waypoint.color()));
        }
    }

    public static void onAddIcon(@Nullable IMinimapCompat source, String name, byte[] icon) {
        if (source != null && source == currentIgnore) {
            return;
        }

        addIcon(source, Minecraft.getInstance().getConnection(), name, icon);

        var packetSplitter = PacketSplitter.get(Minecraft.getInstance().getConnection());
        if (packetSplitter != null && packetSplitter.canSend(AddIconPayload.TYPE)) {
            packetSplitter.send(new AddIconPayload(name, icon));
        }
    }

    public static void onRemoveIcon(@Nullable IMinimapCompat source, String name) {
        if (source != null && source == currentIgnore) {
            return;
        }

        removeIcon(source, Minecraft.getInstance().getConnection(), name);

        if (ClientPlayNetworking.canSend(RemoveIconPayload.TYPE)) {
            ClientPlayNetworking.send(new RemoveIconPayload(name));
        }
    }

    public static void onSetWaypointIcon(@Nullable IMinimapCompat source, Waypoint waypoint) {
        if (source != null && source == currentIgnore) {
            return;
        }

        setWaypointIcon(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.icon());

        if (ClientPlayNetworking.canSend(SetWaypointIconPayload.TYPE)) {
            ClientPlayNetworking.send(new SetWaypointIconPayload(waypoint.name(), waypoint.icon()));
        }
    }
}
