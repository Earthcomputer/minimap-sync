package net.earthcomputer.minimapsync.client;

import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.event.WaypointEvent;
import journeymap.client.api.model.MapImage;
import journeymap.client.waypoint.WaypointStore;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.mixin.journeymap.InternalWaypointAccessor;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.earthcomputer.minimapsync.model.WaypointVisibilityType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class JourneyMapCompat implements IClientPlugin, IMinimapCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static JourneyMapCompat INSTANCE;
    private IClientAPI api;
    private final Set<String> privateWaypoints = new HashSet<>();

    private static final ResourceLocation NETHER = new ResourceLocation("the_nether");
    public static final ResourceLocation ICON_NORMAL = new ResourceLocation("journeymap", "ui/img/waypoint-icon.png");
    private static final ResourceLocation ICON_DEATH = new ResourceLocation("journeymap", "ui/img/waypoint-death-icon.png");
    private static final int ICON_SIZE = 16;

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

    private boolean initializing = false;
    private boolean refreshing = false;
    private final Map<String, journeymap.client.api.display.Waypoint> prevWaypoints = new HashMap<>();
    private static final Map<String, NativeImage> iconImages = new HashMap<>();

    @Override
    public void onEvent(ClientEvent event) {
        if (!MinimapSyncClient.isCompatibleServer()) {
            return;
        }

        if (refreshing) {
            return;
        }

        if (event instanceof WaypointEvent waypointEvent) {
            var waypoint = waypointEvent.waypoint;
            if (isDeathPoint(waypoint)) {
                return;
            }
            switch (waypointEvent.context) {
                case CREATE -> {
                    if (prevWaypoints.containsKey(waypoint.getName())) {
                        int i = 1;
                        String name;
                        do {
                            name = waypoint.getName() + " (" + i + ")";
                        } while (prevWaypoints.containsKey(name));
                        waypoint.setName(name);
                    }

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

    private void refresh(journeymap.client.api.display.Waypoint waypoint) {
        refreshing = true;
        try {
            remove(waypoint);
            api.show(waypoint);
        } catch (Exception e) {
            LOGGER.error("Could not show waypoint", e);
        } finally {
            refreshing = false;
        }
    }

    @Nullable
    private journeymap.client.api.display.Waypoint toJourneyMap(Waypoint waypoint) {
        if (!waypoint.dimensions().isEmpty()) {
            ResourceKey<Level> dim = waypoint.dimensions().iterator().next();
            BlockPos pos = dim == Level.NETHER ? new BlockPos(waypoint.pos().getX() >> 3, waypoint.pos().getY(), waypoint.pos().getZ() >> 3) : waypoint.pos();
            NativeImage iconImage = waypoint.icon() == null ? null : iconImages.get(waypoint.icon());
            return new journeymap.client.api.display.Waypoint(getModId(), waypoint.name(), dim, pos)
                .setDisplayDimensions(waypoint.dimensions().stream().map(d -> d.location().toString()).toArray(String[]::new))
                .setColor(waypoint.color())
                .setIcon(iconImage == null ? null : new MapImage(iconImage, 0, 0, ICON_SIZE, ICON_SIZE, waypoint.color(), 1));
        }
        return null;
    }

    private Waypoint fromJourneyMap(journeymap.client.api.display.Waypoint waypoint) {
        BlockPos pos = NETHER.equals(ResourceLocation.tryParse(waypoint.getDimension()))
            ? new BlockPos(waypoint.getPosition().getX() * 8, waypoint.getPosition().getY(), waypoint.getPosition().getZ() * 8)
            : waypoint.getPosition();
        return new Waypoint(
            waypoint.getName(),
            null,
            Objects.requireNonNullElseGet(waypoint.getColor(), MinimapSync::randomColor),
            Arrays.stream(waypoint.getDisplayDimensions()).map(dim -> {
                ResourceLocation location = ResourceLocation.tryParse(dim);
                return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
            }).filter(Objects::nonNull).collect(Collectors.toSet()),
            pos,
            Minecraft.getInstance().getUser().getProfileId(),
            Minecraft.getInstance().getUser().getName(),
            null,
            System.currentTimeMillis(),
            privateWaypoints.contains(waypoint.getName()),
            WaypointVisibilityType.LOCAL
        );
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void initModel(ClientPacketListener listener, Model model) {
        initializing = true;
        privateWaypoints.clear();
        try {
            for (var waypoint : api.getAllWaypoints()) {
                if (!isDeathPoint(waypoint)) {
                    remove(waypoint);
                }
            }

            for (String iconName : new ArrayList<>(iconImages.keySet())) {
                removeIcon(listener, iconName);
            }
            model.icons().forEach((name, icon) -> addIcon(listener, name, icon));

            model.waypoints().getWaypoints(null).forEach(waypoint -> {
                var wpt = toJourneyMap(waypoint);
                if (wpt != null) {
                    try {
                        api.show(wpt);
                    } catch (Exception e) {
                        LOGGER.error("Could not show waypoint", e);
                    }
                }
                setWaypointIsPrivate(waypoint.name(), waypoint.isPrivate());
            });
        } finally {
            initializing = false;
        }
    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {
        var wpt = toJourneyMap(waypoint);
        if (wpt != null) {
            try {
                api.show(wpt);
            } catch (Exception e) {
                LOGGER.error("Could not show waypoint", e);
            }
        }
        setWaypointIsPrivate(waypoint.name(), waypoint.isPrivate());
    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                remove(waypoint);
            }
        }
    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                waypoint.setDisplayDimensions(dimensions.stream().map(dim -> dim.location().toString()).toArray(String[]::new));
                refresh(waypoint);
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
                    refresh(waypoint);
                }
            }
        }
    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {
        for (var waypoint : api.getAllWaypoints()) {
            if (name.equals(waypoint.getName())) {
                waypoint.setColor(color);
                refresh(waypoint);
            }
        }
    }

    @Override
    public void setWaypointDescription(ClientPacketListener handler, String name, String description) {
    }

    @Override
    public void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule) {
    }

    public static boolean teleport(journeymap.client.waypoint.Waypoint waypoint) {
        if (waypoint.getType() == journeymap.client.waypoint.Waypoint.Type.Death) {
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
        buf.writeUtf(waypoint.getName(), 256);
        buf.writeBoolean(false); // null dimension type (current dimension)
        ClientPlayNetworking.send(MinimapSync.TELEPORT, buf);

        return true;
    }

    @Override
    public void addIcon(ClientPacketListener handler, String name, byte[] icon) {
        NativeImage image;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            image = NativeImage.read(stack.bytes(icon));
        } catch (IOException e) {
            LOGGER.warn("Failed to read image", e);
            return;
        }
        iconImages.put(name, image);

        if (!initializing) {
            Model model = Model.get(handler);
            for (var waypoint : api.getAllWaypoints()) {
                Waypoint modelWaypoint = model.waypoints().getWaypoint(waypoint.getName());
                if (modelWaypoint != null && name.equals(modelWaypoint.icon())) {
                    waypoint.setIcon(new MapImage(image, 0, 0, ICON_SIZE, ICON_SIZE, modelWaypoint.color(), 1));
                    refresh(waypoint);
                }
            }
        }
    }

    @Override
    public void removeIcon(ClientPacketListener handler, String name) {
        NativeImage image = iconImages.remove(name);
        if (image == null) {
            return;
        }

        if (!initializing) {
            Model model = Model.get(handler);
            for (var waypoint : api.getAllWaypoints()) {
                Waypoint modelWaypoint = model.waypoints().getWaypoint(waypoint.getName());
                if (modelWaypoint != null && name.equals(modelWaypoint.icon())) {
                    waypoint.setIcon(null);
                    refresh(waypoint);
                }
            }
        }

        image.close();
    }

    @Override
    public void setWaypointIcon(ClientPacketListener handler, String waypoint, @Nullable String icon) {
        Model model = Model.get(handler);
        if (icon != null && !model.icons().names().contains(icon)) {
            return;
        }

        NativeImage image = icon == null ? null : iconImages.get(icon);
        if (icon != null && image == null) {
            return;
        }

        for (var wpt : api.getAllWaypoints()) {
            if (waypoint.equals(wpt.getName())) {
                wpt.setIcon(image == null ? null : new MapImage(image, 0, 0, ICON_SIZE, ICON_SIZE, Objects.requireNonNullElse(wpt.getColor(), 0xffffff), 1));
                refresh(wpt);
            }
        }
    }

    private void remove(journeymap.client.api.display.Waypoint waypoint) {
        api.remove(waypoint);

        // also remove by unqualified id, because journeymap doesn't remove these properly
        var internalWaypoint = new journeymap.client.waypoint.Waypoint(waypoint);
        ((InternalWaypointAccessor) internalWaypoint).setDisplayId(null);
        WaypointStore.INSTANCE.remove(internalWaypoint, true);
    }

    public void setWaypointIsPrivate(String name, boolean isPrivate) {
        if (isPrivate) {
            privateWaypoints.add(name);
        } else {
            privateWaypoints.remove(name);
        }
    }
}
