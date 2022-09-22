package net.earthcomputer.minimapsync.client;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.event.WaypointEvent;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class JourneyMapCompat implements IClientPlugin, IMinimapCompat {
    private static JourneyMapCompat INSTANCE;
    private IClientAPI api;

    private static final ResourceLocation NETHER = new ResourceLocation("the_nether");
    private static final ResourceLocation ICON_DEATH = new ResourceLocation("journeymap", "ui/img/waypoint-death-icon.png");

    public JourneyMapCompat() {
        INSTANCE = this;
    }

    public static JourneyMapCompat instance() {
        return INSTANCE;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        api.subscribe(getModId(), EnumSet.of(ClientEvent.Type.WAYPOINT));
    }

    @Override
    public String getModId() {
        return "minimapsync";
    }

    private final Map<String, journeymap.client.api.display.Waypoint> prevWaypoints = new HashMap<>();

    @Override
    public void onEvent(ClientEvent event) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }

        if (event instanceof WaypointEvent waypointEvent) {
            var waypoint = waypointEvent.waypoint;
            if (isDeathPoint(waypoint)) {
                return;
            }
            switch (waypointEvent.context) {
                case CREATE -> {
                    prevWaypoints.put(waypoint.getName(), toJourneyMap(fromJourneyMap(waypoint)));
                    MinimapSyncClient.onAddWaypoint(this, fromJourneyMap(waypoint));
                }
                case DELETED -> {
                    MinimapSyncClient.onRemoveWaypoint(this, waypoint.getName());
                    prevWaypoints.remove(waypoint.getName());
                }
                case UPDATE -> {
                    var prevWaypoint = prevWaypoints.get(waypoint.getName());
                    if (prevWaypoint == null) {
                        return;
                    }
                    if (!Arrays.equals(waypoint.getDisplayDimensions(), prevWaypoint.getDisplayDimensions())) {
                        MinimapSyncClient.onSetWaypointDimensions(this, fromJourneyMap(waypoint));
                    }
                    if (!waypoint.getPosition().equals(prevWaypoint.getPosition())) {
                        MinimapSyncClient.onSetWaypointPos(this, fromJourneyMap(waypoint));
                    }
                    if (!Objects.equals(waypoint.getColor(), prevWaypoint.getColor())) {
                        MinimapSyncClient.onSetWaypointColor(this, fromJourneyMap(waypoint));
                    }
                    prevWaypoints.put(waypoint.getName(), toJourneyMap(fromJourneyMap(waypoint)));
                }
            }
        }
    }

    private static boolean isDeathPoint(journeymap.client.api.display.Waypoint waypoint) {
        var icon = waypoint.getIcon();
        return icon != null && ICON_DEATH.equals(icon.getImageLocation());
    }

    @Nullable
    private journeymap.client.api.display.Waypoint toJourneyMap(Waypoint waypoint) {
        if (!waypoint.dimensions().isEmpty()) {
            ResourceKey<Level> dim = waypoint.dimensions().iterator().next();
            BlockPos pos = dim == Level.NETHER ? new BlockPos(waypoint.pos().getX() >> 3, waypoint.pos().getY(), waypoint.pos().getZ() >> 3) : waypoint.pos();
            return new journeymap.client.api.display.Waypoint(getModId(), waypoint.name(), dim, pos)
                .setDisplayDimensions(waypoint.dimensions().stream().map(d -> d.location().toString()).toArray(String[]::new))
                .setColor(waypoint.color());
        }
        return null;
    }

    private static Waypoint fromJourneyMap(journeymap.client.api.display.Waypoint waypoint) {
        BlockPos pos = NETHER.equals(ResourceLocation.tryParse(waypoint.getDimension()))
            ? new BlockPos(waypoint.getPosition().getX() * 8, waypoint.getPosition().getY(), waypoint.getPosition().getZ() * 8)
            : waypoint.getPosition();
        return new Waypoint(
            waypoint.getName(),
            null,
            Objects.requireNonNullElseGet(waypoint.getColor(), MinimapSync::randomColor),
            Arrays.stream(waypoint.getDisplayDimensions()).map(dim -> {
                ResourceLocation location = ResourceLocation.tryParse(dim);
                return location == null ? null : ResourceKey.create(Registry.DIMENSION_REGISTRY, location);
            }).filter(Objects::nonNull).collect(Collectors.toSet()),
            pos,
            Minecraft.getInstance().getUser().getGameProfile().getId(),
            Minecraft.getInstance().getUser().getGameProfile().getName(),
            null
        );
    }

    @Override
    public void initModel(ClientPacketListener listener, Model model) {
        for (var waypoint : api.getAllWaypoints()) {
            if (!isDeathPoint(waypoint)) {
                api.remove(waypoint);
            }
        }

        model.waypoints().getWaypoints(null).forEach(waypoint -> {
            var wpt = toJourneyMap(waypoint);
            if (wpt != null) {
                api.show(wpt);
            }
        });
    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {
        var wpt = toJourneyMap(waypoint);
        if (wpt != null) {
            api.show(wpt);
        }
    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                api.remove(waypoint);
            }
        }
    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                waypoint.setDisplayDimensions(dimensions.stream().map(dim -> dim.location().toString()).toArray(String[]::new));
            }
        }
    }

    @Override
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                String[] dims = waypoint.getDisplayDimensions();
                if (dims.length != 0) {
                    waypoint.setPosition(dims[0], pos);
                }
            }
        }
    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                waypoint.setColor(color);
            }
        }
    }

    @Override
    public void setWaypointDescription(ClientPacketListener handler, String name, String description) {
    }

    @Override
    public void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule) {
    }

    @Override
    public void addIcon(ClientPacketListener handler, String name, byte[] icon) {

    }

    @Override
    public void removeIcon(ClientPacketListener handler, String name) {

    }

    @Override
    public void setWaypointIcon(ClientPacketListener handler, String waypoint, @Nullable String icon) {

    }
}
