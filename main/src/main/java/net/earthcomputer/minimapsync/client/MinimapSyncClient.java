package net.earthcomputer.minimapsync.client;

import com.google.common.collect.ImmutableList;
import net.earthcomputer.minimapsync.FriendlyByteBufUtil;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class MinimapSyncClient implements ClientModInitializer {
    @Nullable
    private static IMinimapCompat currentIgnore;

    private static boolean ready = false;
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
            return builder.build();
        });
    }

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // VoxelMap is bad and late-initializes itself in the tick loop rather than on the join event.
            // When VoxelMap is loaded, we hook into VoxelMap to call onReady() instead.
            if (!FabricLoader.getInstance().isModLoaded("voxelmap")) {
                MinimapSyncClient.onReady();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ready = false;
            whenReady.clear();
        });

        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.PROTOCOL_VERSION, (client, handler, buf, responseSender) -> {
            ((IHasProtocolVersion) handler).minimapsync_setProtocolVersion(Math.min(MinimapSync.CURRENT_PROTOCOL_VERSION, buf.readVarInt()));
            FriendlyByteBuf buf1 = PacketByteBufs.create();
            buf1.writeVarInt(MinimapSync.CURRENT_PROTOCOL_VERSION);
            responseSender.sendPacket(MinimapSync.PROTOCOL_VERSION, buf1);
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.INIT_MODEL, (client, handler, buf, responseSender) -> {
            Model model = new Model(getProtocolVersion(handler), buf);
            client.execute(() -> whenReady(() -> initModel(handler, model)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.ADD_WAYPOINT, (client, handler, buf, responseSender) -> {
            Waypoint waypoint = new Waypoint(getProtocolVersion(handler), buf);
            client.execute(() -> whenReady(() -> addWaypoint(null, handler, waypoint)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.REMOVE_WAYPOINT, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            client.execute(() -> whenReady(() -> removeWaypoint(null, handler, name)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_WAYPOINT_DIMENSIONS, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            Set<ResourceKey<Level>> dimensions = FriendlyByteBufUtil.readCollection(buf, LinkedHashSet::new, buf1 -> FriendlyByteBufUtil.readResourceKey(buf1, Registry.DIMENSION_REGISTRY));
            client.execute(() -> whenReady(() -> setWaypointDimensions(null, handler, name, dimensions)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_WAYPOINT_POS, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> whenReady(() -> setWaypointPos(null, handler, name, pos)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_WAYPOINT_COLOR, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            int color = buf.readInt();
            client.execute(() -> whenReady(() -> setWaypointColor(null, handler, name, color)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_WAYPOINT_DESCRIPTION, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            String description = FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf);
            client.execute(() -> whenReady(() -> setWaypointDescription(null, handler, name, description)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_WAYPOINT_TELEPORT_RULE, (client, handler, buf, responseSender) -> {
            WaypointTeleportRule rule = buf.readEnum(WaypointTeleportRule.class);
            client.execute(() -> whenReady(() -> setWaypointTeleportRule(null, handler, rule)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.ADD_ICON, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            byte[] icon = buf.readByteArray();
            client.execute(() -> whenReady(() -> addIcon(null, handler, name, icon)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.REMOVE_ICON, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            client.execute(() -> whenReady(() -> removeIcon(null, handler, name)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MinimapSync.SET_ICON, (client, handler, buf, responseSender) -> {
            String waypoint = buf.readUtf(256);
            String icon = FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf);
            client.execute(() -> whenReady(() -> setWaypointIcon(null, handler, waypoint, icon)));
        });
    }

    private static void whenReady(Runnable runnable) {
        if (ready) {
            runnable.run();
        } else {
            whenReady.add(runnable);
        }
    }

    public static void onReady() {
        if (ready) return;
        ready = true;

        for (Runnable runnable : whenReady) {
            runnable.run();
        }
        whenReady.clear();
    }

    public static int getProtocolVersion(ClientPacketListener handler) {
        return ((IHasProtocolVersion) handler).minimapsync_getProtocolVersion();
    }

    public static boolean isCompatibleServer() {
        return ClientPlayNetworking.canSend(MinimapSync.ADD_WAYPOINT);
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

    private static void addWaypoint(@Nullable IMinimapCompat source, ClientPacketListener handler, Waypoint waypoint) {
        Model model = Model.get(handler);
        model.waypoints().addWaypoint(waypoint);
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
    }

    private static void removeWaypoint(@Nullable IMinimapCompat source, ClientPacketListener handler, String name) {
        Model model = Model.get(handler);
        model.waypoints().removeWaypoint(name);
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
        model.waypoints().setWaypointDimensions(name, dimensions);
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
        model.waypoints().setPos(name, pos);
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
        model.waypoints().setColor(name, color);
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

    private static void setWaypointDescription(@Nullable IMinimapCompat source, ClientPacketListener handler, String name, String description) {
        Model model = Model.get(handler);
        model.waypoints().setDescription(name, description);
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
        Model.get(handler).waypoints().setIcon(waypoint, icon);
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
        addWaypoint(source, connection, waypoint);

        if (ClientPlayNetworking.canSend(MinimapSync.ADD_WAYPOINT)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            waypoint.toPacket(getProtocolVersion(connection), buf);
            ClientPlayNetworking.send(MinimapSync.ADD_WAYPOINT, buf);
        }
    }

    public static void onRemoveWaypoint(IMinimapCompat source, String name) {
        if (source == currentIgnore) {
            return;
        }

        removeWaypoint(source, Minecraft.getInstance().getConnection(), name);

        if (ClientPlayNetworking.canSend(MinimapSync.REMOVE_WAYPOINT)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(name, 256);
            ClientPlayNetworking.send(MinimapSync.REMOVE_WAYPOINT, buf);
        }
    }

    public static void onSetWaypointDimensions(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointDimensions(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.dimensions());

        if (ClientPlayNetworking.canSend(MinimapSync.SET_WAYPOINT_DIMENSIONS)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(waypoint.name(), 256);
            FriendlyByteBufUtil.writeCollection(buf, waypoint.dimensions(), FriendlyByteBufUtil::writeResourceKey);
            ClientPlayNetworking.send(MinimapSync.SET_WAYPOINT_DIMENSIONS, buf);
        }
    }

    public static void onSetWaypointPos(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointPos(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.pos());

        if (ClientPlayNetworking.canSend(MinimapSync.SET_WAYPOINT_POS)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(waypoint.name(), 256);
            buf.writeBlockPos(waypoint.pos());
            ClientPlayNetworking.send(MinimapSync.SET_WAYPOINT_POS, buf);
        }
    }

    public static void onSetWaypointColor(IMinimapCompat source, Waypoint waypoint) {
        if (source == currentIgnore) {
            return;
        }

        setWaypointColor(source, Minecraft.getInstance().getConnection(), waypoint.name(), waypoint.color());

        if (ClientPlayNetworking.canSend(MinimapSync.SET_WAYPOINT_COLOR)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(waypoint.name(), 256);
            buf.writeInt(waypoint.color());
            ClientPlayNetworking.send(MinimapSync.SET_WAYPOINT_COLOR, buf);
        }
    }
}
