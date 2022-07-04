package net.earthcomputer.minimapsync.client;

import com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap;
import com.mamiyaotaru.voxelmap.interfaces.IDimensionManager;
import com.mamiyaotaru.voxelmap.interfaces.IWaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static double getCoordinateScale(ResourceKey<Level> dimension) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            DimensionType dimensionType = connection.registryAccess()
                .registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
                .get(dimension.location());
            if (dimensionType != null) {
                return dimensionType.coordinateScale();
            }
        }

        return 1;
    }

    private static com.mamiyaotaru.voxelmap.util.Waypoint toVoxel(Waypoint waypoint) {
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        IDimensionManager dimensionManager = AbstractVoxelMap.getInstance().getDimensionManager();
        DimensionContainer currentDimension = dimensionManager.getDimensionContainerByIdentifier(waypoint.dimension().location().toString());
        double scale = getCoordinateScale(waypoint.dimension());

        return new com.mamiyaotaru.voxelmap.util.Waypoint(
            waypoint.name(),
            Mth.floor(waypoint.pos().getX() * scale),
            Mth.floor(waypoint.pos().getZ() * scale),
            waypoint.pos().getY(),
            true,
            ((waypoint.color() >> 16) & 0xff) / 255f,
            ((waypoint.color() >> 8) & 0xff) / 255f,
            (waypoint.color() & 0xff) / 255f,
            "",
            waypointManager.getCurrentSubworldDescriptor(false),
            new TreeSet<>(List.of(currentDimension))
        );
    }

    public static Waypoint fromVoxel(com.mamiyaotaru.voxelmap.util.Waypoint waypoint) {
        ResourceKey<Level> dimension = waypoint.dimensions.isEmpty()
            ? Level.OVERWORLD
            : ResourceKey.create(Registry.DIMENSION_REGISTRY, waypoint.dimensions.iterator().next().resourceLocation);
        double scale = 1 / getCoordinateScale(dimension);
        return new Waypoint(
            waypoint.name,
            null,
            (((int) (waypoint.red * 255) & 0xff) << 16) | (((int) (waypoint.green * 255) & 0xff) << 8) | ((int) (waypoint.blue * 255) & 0xff),
            dimension,
            new BlockPos(Mth.floor(waypoint.x * scale), waypoint.y, Mth.floor(waypoint.z * scale)),
            Minecraft.getInstance().getUser().getGameProfile().getId(),
            Minecraft.getInstance().getUser().getGameProfile().getName()
        );
    }

    private void onWorldTick(ClientLevel level) {
        if (++tickCounter % 20 != 0) {
            return;
        }

        var voxelWaypoints = AbstractVoxelMap.getInstance().getWaypointManager().getWaypoints();
        var serverWaypoints = serverKnownWaypoints.stream().collect(Collectors.toMap(Waypoint::name, Function.identity()));
        for (var voxelWaypoint : voxelWaypoints) {
            var serverWaypoint = serverWaypoints.remove(voxelWaypoint.name);
            if (serverWaypoint == null) {
                serverWaypoint = fromVoxel(voxelWaypoint);
                serverKnownWaypoints.add(serverWaypoint);
                MinimapSyncClient.onAddWaypoint(this, serverWaypoint);
            } else {
                ResourceLocation serverDimension = serverWaypoint.dimension().location();
                if (!voxelWaypoint.dimensions.isEmpty() && voxelWaypoint.dimensions.stream().noneMatch(dim -> dim.resourceLocation.equals(serverDimension))) {
                    serverKnownWaypoints.removeIf(waypoint -> waypoint.name().equals(voxelWaypoint.name));
                    MinimapSyncClient.onRemoveWaypoint(this, serverWaypoint);
                    serverWaypoint = fromVoxel(voxelWaypoint);
                    serverKnownWaypoints.add(serverWaypoint);
                    MinimapSyncClient.onAddWaypoint(this, serverWaypoint);
                }

                double scale = 1 / getCoordinateScale(serverWaypoint.dimension());

                if (Mth.floor(voxelWaypoint.x * scale) != serverWaypoint.pos().getX() || voxelWaypoint.y != serverWaypoint.pos().getY() || Mth.floor(voxelWaypoint.z * scale) != serverWaypoint.pos().getZ()) {
                    serverWaypoint = serverWaypoint.withPos(new BlockPos(Mth.floor(voxelWaypoint.x * scale), voxelWaypoint.y, Mth.floor(voxelWaypoint.z * scale)));
                    MinimapSyncClient.onSetWaypointPos(this, serverWaypoint);
                }

                int color = (((int) (voxelWaypoint.red * 255) & 0xff) << 16) | (((int) (voxelWaypoint.green * 255) & 0xff) << 8) | ((int) (voxelWaypoint.blue * 255) & 0xff);
                if (color != serverWaypoint.color()) {
                    serverWaypoint = serverWaypoint.withColor(color);
                    MinimapSyncClient.onSetWaypointColor(this, serverWaypoint);
                }
            }
        }

        for (Waypoint removedWaypoint : serverWaypoints.values()) {
            serverKnownWaypoints.remove(removedWaypoint);
            MinimapSyncClient.onRemoveWaypoint(this, removedWaypoint);
        }
    }

    @Override
    public void initModel(ClientPacketListener listener, Model model) {
        serverKnownWaypoints.clear();
        model.waypoints().getWaypoints(null).forEach(serverKnownWaypoints::add);

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        for (var waypoint : new ArrayList<>(waypointManager.getWaypoints())) {
            waypointManager.deleteWaypoint(waypoint);
        }
        for (var waypoint : (Iterable<Waypoint>) model.waypoints().getWaypoints(null)::iterator) {
            waypointManager.addWaypoint(toVoxel(waypoint));
        }
        waypointManager.saveWaypoints();
    }

    public void mergeWaypoints() {
        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();

        Map<String, com.mamiyaotaru.voxelmap.util.Waypoint> waypoints = new LinkedHashMap<>();
        for (var waypoint : waypointManager.getWaypoints()) {
            waypoints.put(waypoint.name, waypoint);
        }
        for (var waypoint : waypoints.values()) {
            waypointManager.deleteWaypoint(waypoint);
        }

        for (var serverWaypoint : serverKnownWaypoints) {
            var voxelWaypoint = waypoints.get(serverWaypoint.name());
            var newVoxelWaypoint = toVoxel(serverWaypoint);
            if (voxelWaypoint == null) {
                waypointManager.addWaypoint(newVoxelWaypoint);
            } else {
                voxelWaypoint.dimensions = newVoxelWaypoint.dimensions;
                voxelWaypoint.x = newVoxelWaypoint.x;
                voxelWaypoint.y = newVoxelWaypoint.y;
                voxelWaypoint.z = newVoxelWaypoint.z;
                voxelWaypoint.red = newVoxelWaypoint.red;
                voxelWaypoint.green = newVoxelWaypoint.green;
                voxelWaypoint.blue = newVoxelWaypoint.blue;
                waypointManager.addWaypoint(voxelWaypoint);
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
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {
        ResourceKey<Level> dimension = null;
        for (int i = 0; i < serverKnownWaypoints.size(); i++) {
            Waypoint serverWaypoint = serverKnownWaypoints.get(i);
            if (name.equals(serverWaypoint.name())) {
                serverKnownWaypoints.set(i, serverWaypoint.withPos(pos));
                dimension = serverWaypoint.dimension();
            }
        }
        if (dimension == null) {
            return;
        }
        double scale = getCoordinateScale(dimension);

        IWaypointManager waypointManager = AbstractVoxelMap.getInstance().getWaypointManager();
        boolean changed = false;
        for (var waypoint : waypointManager.getWaypoints()) {
            if (name.equals(waypoint.name)) {
                waypoint.x = Mth.floor(pos.getX() * scale);
                waypoint.y = pos.getY();
                waypoint.z = Mth.floor(pos.getZ() * scale);
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
}
