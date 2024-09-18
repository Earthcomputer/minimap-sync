package net.earthcomputer.minimapsync.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import xaero.common.graphics.CustomRenderTypes;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.settings.ModSettings;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.state.MinimapWorldState;
import xaero.hud.path.XaeroPath;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public enum XaerosMapCompat implements IMinimapCompat {
    INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> symbolToIcon = new HashMap<>();
    private static final Map<String, DynamicTexture> iconTextures = new HashMap<>();

    private boolean ready = false;

    public XaerosMapCompat init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> XaerosMapCompat.INSTANCE.ready = false);
        return this;
    }

    private static xaero.common.minimap.waypoints.Waypoint toXaeros(Waypoint waypoint) {
        return new xaero.common.minimap.waypoints.Waypoint(
            waypoint.pos().getX(),
            waypoint.pos().getY(),
            waypoint.pos().getZ(),
            waypoint.name(),
            makeSymbol(waypoint.name(), waypoint.icon()),
            getClosestColor(waypoint.color())
        );
    }

    public void onReady() {
        this.ready = true;
        MinimapSyncClient.checkReady();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void initModel(ClientPacketListener listener, Model model) {
        symbolToIcon.clear();
        for (DynamicTexture icon : iconTextures.values()) {
            icon.close();
        }
        iconTextures.clear();

        Set<MinimapWorld> modifiedWorlds = new HashSet<>();

        for (MinimapWorld waypointWorld : getWaypointWorlds()) {
            WaypointSet waypointSet = getWaypointSet(waypointWorld);
            if (removeIf(waypointSet, XaerosMapCompat::shouldSyncWithServer)) {
                modifiedWorlds.add(waypointWorld);
            }
        }

        for (var waypoint : (Iterable<Waypoint>) model.waypoints().getWaypoints(null)::iterator) {
            for (ResourceKey<Level> dimension : waypoint.dimensions()) {
                MinimapWorld waypointWorld = getWaypointWorld(dimension);
                getWaypointSet(waypointWorld).add(toXaeros(waypoint));
                modifiedWorlds.add(waypointWorld);
            }
        }

        for (MinimapWorld modifiedWorld : modifiedWorlds) {
            saveWaypoints(modifiedWorld);
        }

        model.icons().icons().forEach((name, bytes) -> addIcon(listener, name, bytes));
    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            MinimapWorld waypointWorld = getWaypointWorld(dimension);
            getWaypointSet(waypointWorld).add(toXaeros(waypoint));
            saveWaypoints(waypointWorld);
        }
    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {
        for (MinimapWorld waypointWorld : getWaypointWorlds()) {
            if (removeIf(getWaypointSet(waypointWorld), wpt -> name.equals(wpt.getName()))) {
                saveWaypoints(waypointWorld);
            }
        }
    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        Set<MinimapWorld> newWaypointWorlds = dimensions.stream().map(XaerosMapCompat::getWaypointWorld).collect(Collectors.toSet());
        for (MinimapWorld waypointWorld : getWaypointWorlds()) {
            WaypointSet waypointSet = getWaypointSet(waypointWorld);
            if (newWaypointWorlds.contains(waypointWorld)) {
                if (stream(waypointSet).noneMatch(wpt -> name.equals(wpt.getName()))) {
                    waypointSet.add(toXaeros(waypoint));
                    saveWaypoints(waypointWorld);
                }
            } else {
                if (removeIf(waypointSet, wpt -> name.equals(wpt.getName()))) {
                    saveWaypoints(waypointWorld);
                }
            }
        }
    }

    @Override
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {
        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            MinimapWorld waypointWorld = getWaypointWorld(dimension);
            stream(getWaypointSet(waypointWorld)).filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setX(pos.getX());
                wpt.setY(pos.getY());
                wpt.setZ(pos.getZ());
            });
            saveWaypoints(waypointWorld);
        }
    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {
        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        int xaeroColor = getClosestColor(color);
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            MinimapWorld waypointWorld = getWaypointWorld(dimension);
            stream(getWaypointSet(waypointWorld)).filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setColor(xaeroColor);
            });
            saveWaypoints(waypointWorld);
        }
    }

    @Override
    public void setWaypointDescription(ClientPacketListener handler, String name, @Nullable String description) {
    }

    @Override
    public void setWaypointTeleportRule(ClientPacketListener handler, WaypointTeleportRule rule) {
    }

    @Override
    public void addIcon(ClientPacketListener handler, String name, byte[] icon) {
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(icon));
        } catch (IOException e) {
            LOGGER.error("Icon had invalid format");
            return;
        }

        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(getIconResourceLocation(name), texture);
        DynamicTexture old = iconTextures.put(name, texture);
        if (old != null) {
            old.close();
        }
    }

    @Override
    public void removeIcon(ClientPacketListener handler, String name) {
        DynamicTexture icon = iconTextures.remove(name);
        if (icon != null) {
            icon.close();
        }
    }

    @Override
    public void setWaypointIcon(ClientPacketListener handler, String name, @Nullable String icon) {
        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        String symbol = makeSymbol(name, icon);
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            MinimapWorld waypointWorld = getWaypointWorld(dimension);
            stream(getWaypointSet(waypointWorld)).filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setSymbol(symbol);
            });
            saveWaypoints(waypointWorld);
        }
    }

    private static boolean shouldSyncWithServer(xaero.common.minimap.waypoints.Waypoint waypoint) {
        return waypoint.getWaypointType() == 0 && !waypoint.isTemporary() && !waypoint.isOneoffDestination();
    }

    private static MinimapWorld getWaypointWorld(ResourceKey<Level> dimension) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorldManager worldManager = session.getWorldManager();
        MinimapWorldState worldState = session.getWorldState();
        XaeroPath worldPath = worldState.getAutoRootContainerPath()
            .resolve(session.getDimensionHelper().getDimensionDirectoryName(dimension))
            .resolve(worldState.getAutoWorldPath());
        return worldManager.getWorld(worldPath);
    }

    private static WaypointSet getWaypointSet(MinimapWorld waypointWorld) {
        WaypointSet waypointSet = waypointWorld.getWaypointSet("gui.xaero_default");
        if (waypointSet != null) {
            return waypointSet;
        }
        return waypointWorld.getCurrentWaypointSet();
    }

    private static Stream<xaero.common.minimap.waypoints.Waypoint> stream(WaypointSet waypointSet) {
        return StreamSupport.stream(waypointSet.getWaypoints().spliterator(), false);
    }

    private static boolean removeIf(WaypointSet waypointSet, Predicate<xaero.common.minimap.waypoints.Waypoint> predicate) {
        boolean changed = false;
        for (int i = 0; i < waypointSet.size(); i++) {
            if (predicate.test(waypointSet.get(i))) {
                waypointSet.remove(i--);
                changed = true;
            }
        }
        return changed;
    }

    private static List<MinimapWorld> getWaypointWorlds() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorldManager worldManager = session.getWorldManager();
        MinimapWorldState worldState = session.getWorldState();
        MinimapWorldContainer rootContainer = worldManager.getWorldContainer(worldState.getAutoRootContainerPath());
        List<MinimapWorld> result = new ArrayList<>();
        for (MinimapWorldContainer dim : rootContainer.getSubContainers()) {
            result.add(dim.addWorld(Optionull.map(worldState.getAutoWorldPath(), XaeroPath::getLastNode)));
        }
        return result;
    }

    private static String makeSymbol(String waypointName, @Nullable String iconName) {
        if (iconName == null) {
            return waypointName.isEmpty() ? "X" : waypointName.substring(0, 1).toUpperCase();
        }
        String symbol = "minimapsync_" + MinimapSync.makeFileSafeString(iconName);
        symbolToIcon.put(symbol, iconName);
        return symbol;
    }

    private static ResourceLocation getIconResourceLocation(String icon) {
        return ResourceLocation.fromNamespaceAndPath("minimapsync", "xaeros_" + MinimapSync.makeResourceSafeString(icon));
    }

    private static int getClosestColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;

        double minDistanceSq = Double.POSITIVE_INFINITY;
        int closestColor = 0;
        for (int c = 0; c < ModSettings.COLORS.length; c++) {
            int color1 = ModSettings.COLORS[c];
            int red1 = (color1 >> 16) & 0xff;
            int green1 = (color1 >> 8) & 0xff;
            int blue1 = color1 & 0xff;

            // https://en.wikipedia.org/wiki/Color_difference#sRGB
            double redMean = (red + red1) * 0.5;
            int deltaRed = red1 - red;
            int deltaGreen = green1 - green;
            int deltaBlue = blue1 - blue;
            double distanceSq = (2 + redMean * (1. / 256)) * (deltaRed * deltaRed) + 4 * deltaGreen * deltaGreen + (2 + (255 - redMean) * (1. / 256)) * (deltaBlue * deltaBlue);
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestColor = c;
            }
        }

        return closestColor;
    }

    private static void saveWaypoints(MinimapWorld waypointWorld) {
        try {
            waypointWorld.getContainer().getSession().getWorldManagerIO().saveWorld(waypointWorld);
        } catch (IOException e) {
            LOGGER.error("Failed to save waypoints", e);
        }
    }

    public static void drawCustomIcon(PoseStack matrixStack, String icon, float x, float y, int width, int height, float r, float g, float b, float a) {
        icon = symbolToIcon.get(icon);
        if (icon == null) {
            return;
        }
        if (!iconTextures.containsKey(icon)) {
            return;
        }

        MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers = BuiltInHudModules.MINIMAP.getCurrentSession().getMultiTextureRenderTypeRenderers();;
        MultiTextureRenderTypeRenderer renderer = multiTextureRenderTypeRenderers.getRenderer(textureId -> RenderSystem.setShaderTexture(0, textureId), MultiTextureRenderTypeRendererProvider::defaultTextureBind, CustomRenderTypes.GUI_NEAREST);

        int textureId = Minecraft.getInstance().getTextureManager().getTexture(getIconResourceLocation(icon)).getId();
        BufferBuilder buffer = renderer.begin(textureId);
        Matrix4f pose = matrixStack.last().pose();

        buffer.addVertex(pose, x, y, 0).setColor(r, g, b, a).setUv(0, 0);
        buffer.addVertex(pose, x, y + height, 0).setColor(r, g, b, a).setUv(0, 1);
        buffer.addVertex(pose, x + width, y + height, 0).setColor(r, g, b, a).setUv(1, 1);
        buffer.addVertex(pose, x + width, y, 0).setColor(r, g, b, a).setUv(1, 0);

        multiTextureRenderTypeRenderers.draw(renderer);
    }
}
