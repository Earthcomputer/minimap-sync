package net.earthcomputer.minimapsync.client;

import com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap;
import com.mamiyaotaru.voxelmap.interfaces.IDimensionManager;
import com.mamiyaotaru.voxelmap.interfaces.IWaypointManager;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum VoxelMapCompat implements IMinimapCompat {
    INSTANCE;

    public VoxelMapCompat init() {
        ClientTickEvents.START_WORLD_TICK.register(this::onWorldTick);
        return this;
    }

    private int tickCounter = 0;
    private final List<Waypoint> serverKnownWaypoints = new ArrayList<>();

    private static boolean isDeathpoint(com.mamiyaotaru.voxelmap.util.Waypoint waypoint) {
        return waypoint.imageSuffix.equals("skull");
    }

    private static com.mamiyaotaru.voxelmap.util.Waypoint toVoxel(Waypoint waypoint) {
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        IDimensionManager dimensionManager = AbstractVoxelMap.getInstance().getDimensionManager();

        return new com.mamiyaotaru.voxelmap.util.Waypoint(
            waypoint.name(),
            waypoint.pos().getX(),
            waypoint.pos().getZ(),
            waypoint.pos().getY(),
            true,
            ((waypoint.color() >> 16) & 0xff) / 255f,
            ((waypoint.color() >> 8) & 0xff) / 255f,
            (waypoint.color() & 0xff) / 255f,
            "",
            waypointManager.getCurrentSubworldDescriptor(false),
            waypoint.dimensions().stream()
                .map(dim -> dimensionManager.getDimensionContainerByIdentifier(dim.location().toString()))
                .collect(Collectors.toCollection(TreeSet::new))
        );
    }

    public static Waypoint fromVoxel(com.mamiyaotaru.voxelmap.util.Waypoint waypoint) {
        return new Waypoint(
            waypoint.name,
            null,
            (((int) (waypoint.red * 255) & 0xff) << 16) | (((int) (waypoint.green * 255) & 0xff) << 8) | ((int) (waypoint.blue * 255) & 0xff),
            waypoint.dimensions.stream()
                .map(dim -> ResourceKey.create(Registry.DIMENSION_REGISTRY, dim.resourceLocation))
                .collect(Collectors.toCollection(LinkedHashSet::new)),
            new BlockPos(waypoint.x, waypoint.y, waypoint.z),
            Minecraft.getInstance().getUser().getGameProfile().getId(),
            Minecraft.getInstance().getUser().getGameProfile().getName()
        );
    }

    private void onWorldTick(ClientLevel level) {
        if (++tickCounter % 20 != 0) {
            return;
        }

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();

        // delete duplicate waypoints
        Set<String> seenNames = new HashSet<>();
        for (var voxelWaypoint : new ArrayList<>(waypointManager.getWaypoints())) {
            if (!seenNames.add(voxelWaypoint.name)) {
                waypointManager.deleteWaypoint(voxelWaypoint);
            }
        }

        var voxelWaypoints = waypointManager.getWaypoints();
        var serverWaypoints = serverKnownWaypoints.stream().collect(Collectors.toMap(Waypoint::name, Function.identity()));
        boolean changed = false;
        for (var voxelWaypoint : voxelWaypoints) {
            if (isDeathpoint(voxelWaypoint)) {
                continue;
            }

            var serverWaypoint = serverWaypoints.remove(voxelWaypoint.name);
            if (serverWaypoint == null) {
                serverWaypoint = fromVoxel(voxelWaypoint);
                MinimapSyncClient.onAddWaypoint(this, serverWaypoint);
                changed = true;
            } else {
                Set<ResourceKey<Level>> voxelDimensions = voxelWaypoint.dimensions.stream()
                    .map(dim -> ResourceKey.create(Registry.DIMENSION_REGISTRY, dim.resourceLocation))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!voxelDimensions.equals(serverWaypoint.dimensions())) {
                    serverWaypoint = serverWaypoint.withDimensions(voxelDimensions);
                    MinimapSyncClient.onSetWaypointDimensions(this, serverWaypoint);
                    changed = true;
                }

                if (voxelWaypoint.x != serverWaypoint.pos().getX() || voxelWaypoint.y != serverWaypoint.pos().getY() || voxelWaypoint.z != serverWaypoint.pos().getZ()) {
                    serverWaypoint = serverWaypoint.withPos(new BlockPos(voxelWaypoint.x, voxelWaypoint.y, voxelWaypoint.z));
                    MinimapSyncClient.onSetWaypointPos(this, serverWaypoint);
                    changed = true;
                }

                int color = (((int) (voxelWaypoint.red * 255) & 0xff) << 16) | (((int) (voxelWaypoint.green * 255) & 0xff) << 8) | ((int) (voxelWaypoint.blue * 255) & 0xff);
                if (color != serverWaypoint.color()) {
                    serverWaypoint = serverWaypoint.withColor(color);
                    MinimapSyncClient.onSetWaypointColor(this, serverWaypoint);
                    changed = true;
                }
            }
        }

        for (Waypoint removedWaypoint : serverWaypoints.values()) {
            MinimapSyncClient.onRemoveWaypoint(this, removedWaypoint.name());
            changed = true;
        }

        if (changed) {
            serverKnownWaypoints.clear();
            for (var waypoint : voxelWaypoints) {
                if (isDeathpoint(waypoint)) {
                    continue;
                }
                serverKnownWaypoints.add(fromVoxel(waypoint));
            }
        }
    }

    @Override
    public void initModel(ClientPacketListener listener, Model model) {
        serverKnownWaypoints.clear();
        model.waypoints().getWaypoints(null).forEach(serverKnownWaypoints::add);

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        for (var waypoint : new ArrayList<>(waypointManager.getWaypoints())) {
            if (!isDeathpoint(waypoint)) {
                waypointManager.deleteWaypoint(waypoint);
            }
        }
        for (var waypoint : (Iterable<Waypoint>) model.waypoints().getWaypoints(null)::iterator) {
            waypointManager.addWaypoint(toVoxel(waypoint));
        }
        waypointManager.saveWaypoints();
    }

    public void mergeWaypoints(ArrayList<com.mamiyaotaru.voxelmap.util.Waypoint> wayPts) {
        var waypoints = wayPts.stream().collect(Collectors.groupingBy(waypoint -> waypoint.name));
        wayPts.removeIf(wp -> !isDeathpoint(wp));

        for (var serverWaypoint : serverKnownWaypoints) {
            var voxelWpts = waypoints.get(serverWaypoint.name());
            var newVoxelWaypoint = toVoxel(serverWaypoint);
            if (voxelWpts == null || voxelWpts.isEmpty()) {
                wayPts.add(newVoxelWaypoint);
            } else {
                var voxelWaypoint = voxelWpts.get(0);
                voxelWaypoint.dimensions = newVoxelWaypoint.dimensions;
                voxelWaypoint.x = newVoxelWaypoint.x;
                voxelWaypoint.y = newVoxelWaypoint.y;
                voxelWaypoint.z = newVoxelWaypoint.z;
                voxelWaypoint.red = newVoxelWaypoint.red;
                voxelWaypoint.green = newVoxelWaypoint.green;
                voxelWaypoint.blue = newVoxelWaypoint.blue;
                wayPts.add(voxelWaypoint);
            }
        }
    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {
        serverKnownWaypoints.add(waypoint);
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        waypointManager.addWaypoint(toVoxel(waypoint));
        waypointManager.saveWaypoints();
    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {
        serverKnownWaypoints.removeIf(it -> name.equals(it.name()));
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        for (var waypoint : new ArrayList<>(waypointManager.getWaypoints())) {
            if (name.equals(waypoint.name)) {
                waypointManager.deleteWaypoint(waypoint);
            }
        }
        waypointManager.saveWaypoints();
    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        for (int i = 0; i < serverKnownWaypoints.size(); i++) {
            Waypoint serverWaypoint = serverKnownWaypoints.get(i);
            if (name.equals(serverWaypoint.name())) {
                serverKnownWaypoints.set(i, serverWaypoint.withDimensions(dimensions));
            }
        }

        IDimensionManager dimensionManager = AbstractVoxelMap.getInstance().getDimensionManager();
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        boolean changed = false;
        for (var waypoint : waypointManager.getWaypoints()) {
            if (name.equals(waypoint.name)) {
                waypoint.dimensions = dimensions.stream()
                    .map(dim -> dimensionManager.getDimensionContainerByIdentifier(dim.location().toString()))
                    .collect(Collectors.toCollection(TreeSet::new));
                changed = true;
            }
        }
        if (changed) {
            waypointManager.saveWaypoints();
        }
    }

    @Override
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {
        for (int i = 0; i < serverKnownWaypoints.size(); i++) {
            Waypoint serverWaypoint = serverKnownWaypoints.get(i);
            if (name.equals(serverWaypoint.name())) {
                serverKnownWaypoints.set(i, serverWaypoint.withPos(pos));
            }
        }

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        boolean changed = false;
        for (var waypoint : waypointManager.getWaypoints()) {
            if (name.equals(waypoint.name)) {
                waypoint.x = pos.getX();
                waypoint.y = pos.getY();
                waypoint.z = pos.getZ();
                changed = true;
            }
        }
        if (changed) {
            waypointManager.saveWaypoints();
        }
    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {
        for (int i = 0; i < serverKnownWaypoints.size(); i++) {
            Waypoint serverWaypoint = serverKnownWaypoints.get(i);
            if (name.equals(serverWaypoint.name())) {
                serverKnownWaypoints.set(i, serverWaypoint.withColor(color));
            }
        }

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        boolean changed = false;
        for (var waypoint : waypointManager.getWaypoints()) {
            if (name.equals(waypoint.name)) {
                waypoint.red = ((color >> 16) & 0xff) / 255.0f;
                waypoint.green = ((color >> 8) & 0xff) / 255.0f;
                waypoint.blue = (color & 0xff) / 255.0f;
                changed = true;
            }
        }
        if (changed) {
            waypointManager.saveWaypoints();
        }
    }

    @Override
    public void setWaypointDescription(ClientPacketListener handler, String name, String description) {
    }

    @Override
    public void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule) {
    }

    public boolean teleport(com.mamiyaotaru.voxelmap.util.Waypoint waypoint) {
        if (isDeathpoint(waypoint)) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (!Model.get(player.connection).teleportRule().canTeleport(player)) {
            return false;
        }
        if (!ClientPlayNetworking.canSend(MinimapSync.TELEPORT)) {
            return false;
        }

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(waypoint.name, 256);
        buf.writeBoolean(false); // null dimension type (current dimension)
        ClientPlayNetworking.send(MinimapSync.TELEPORT, buf);

        return true;
    }
}
