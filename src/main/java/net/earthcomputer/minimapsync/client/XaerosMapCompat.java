package net.earthcomputer.minimapsync.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Matrix4f;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import xaero.common.AXaeroMinimap;
import xaero.common.XaeroMinimapSession;
import xaero.common.graphics.CustomRenderTypes;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.waypoints.WaypointSet;
import xaero.common.minimap.waypoints.WaypointWorld;
import xaero.common.minimap.waypoints.WaypointWorldContainer;
import xaero.common.minimap.waypoints.WaypointsManager;
import xaero.common.settings.ModSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        symbolToIcon.clear();
        for (DynamicTexture icon : iconTextures.values()) {
            icon.close();
        }
        iconTextures.clear();

        Set<WaypointWorld> modifiedWorlds = new HashSet<>();

        WaypointsManager waypointsManager = session.getWaypointsManager();
        for (WaypointWorld waypointWorld : getWaypointWorlds(waypointsManager)) {
            WaypointSet waypointSet = getWaypointSet(waypointWorld);
            if (waypointSet.getList().removeIf(XaerosMapCompat::shouldSyncWithServer)) {
                modifiedWorlds.add(waypointWorld);
            }
        }

        for (var waypoint : (Iterable<Waypoint>) model.waypoints().getWaypoints(null)::iterator) {
            for (ResourceKey<Level> dimension : waypoint.dimensions()) {
                WaypointWorld waypointWorld = getWaypointWorld(waypointsManager, dimension);
                getWaypointSet(waypointWorld).getList().add(toXaeros(waypoint));
                modifiedWorlds.add(waypointWorld);
            }
        }

        for (WaypointWorld modifiedWorld : modifiedWorlds) {
            saveWaypoints(modifiedWorld);
        }

        model.icons().icons().forEach((name, bytes) -> addIcon(listener, name, bytes));
    }

    @Override
    public void addWaypoint(ClientPacketListener listener, Waypoint waypoint) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            WaypointWorld waypointWorld = getWaypointWorld(waypointsManager, dimension);
            getWaypointSet(waypointWorld).getList().add(toXaeros(waypoint));
            saveWaypoints(waypointWorld);
        }
    }

    @Override
    public void removeWaypoint(ClientPacketListener listener, String name) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        for (WaypointWorld waypointWorld : getWaypointWorlds(waypointsManager)) {
            if (getWaypointSet(waypointWorld).getList().removeIf(wpt -> name.equals(wpt.getName()))) {
                saveWaypoints(waypointWorld);
            }
        }
    }

    @Override
    public void setWaypointDimensions(ClientPacketListener handler, String name, Set<ResourceKey<Level>> dimensions) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        Set<WaypointWorld> newWaypointWorlds = dimensions.stream().map(dim -> getWaypointWorld(waypointsManager, dim)).collect(Collectors.toSet());
        for (WaypointWorld waypointWorld : getWaypointWorlds(waypointsManager)) {
            WaypointSet waypointSet = getWaypointSet(waypointWorld);
            if (newWaypointWorlds.contains(waypointWorld)) {
                if (waypointSet.getList().stream().noneMatch(wpt -> name.equals(wpt.getName()))) {
                    waypointSet.getList().add(toXaeros(waypoint));
                    saveWaypoints(waypointWorld);
                }
            } else {
                if (waypointSet.getList().removeIf(wpt -> name.equals(wpt.getName()))) {
                    saveWaypoints(waypointWorld);
                }
            }
        }
    }

    @Override
    public void setWaypointPos(ClientPacketListener handler, String name, BlockPos pos) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            WaypointWorld waypointWorld = getWaypointWorld(waypointsManager, dimension);
            getWaypointSet(waypointWorld).getList().stream().filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setX(pos.getX());
                wpt.setY(pos.getY());
                wpt.setZ(pos.getZ());
            });
            saveWaypoints(waypointWorld);
        }
    }

    @Override
    public void setWaypointColor(ClientPacketListener handler, String name, int color) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        int xaeroColor = getClosestColor(color);
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            WaypointWorld waypointWorld = getWaypointWorld(waypointsManager, dimension);
            getWaypointSet(waypointWorld).getList().stream().filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setColor(xaeroColor);
            });
            saveWaypoints(waypointWorld);
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
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        Waypoint waypoint = Model.get(handler).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return;
        }

        WaypointsManager waypointsManager = session.getWaypointsManager();
        String symbol = makeSymbol(name, icon);
        for (ResourceKey<Level> dimension : waypoint.dimensions()) {
            WaypointWorld waypointWorld = getWaypointWorld(waypointsManager, dimension);
            getWaypointSet(waypointWorld).getList().stream().filter(w -> name.equals(w.getName())).findAny().ifPresent(wpt -> {
                wpt.setSymbol(symbol);
            });
            saveWaypoints(waypointWorld);
        }
    }

    private static boolean shouldSyncWithServer(xaero.common.minimap.waypoints.Waypoint waypoint) {
        return waypoint.getWaypointType() == 0 && !waypoint.isTemporary() && !waypoint.isOneoffDestination();
    }

    private static WaypointWorld getWaypointWorld(WaypointsManager manager, ResourceKey<Level> dimension) {
        String rootContainer = manager.getAutoRootContainerID();
        String dimensionName = manager.getDimensionDirectoryName(dimension);
        return manager.getWorld(rootContainer + "/" + dimensionName, manager.getAutoWorldID());
    }

    private static WaypointSet getWaypointSet(WaypointWorld waypointWorld) {
        WaypointSet waypointSet = waypointWorld.getSets().get("gui.xaero_default");
        if (waypointSet != null) {
            return waypointSet;
        }
        return waypointWorld.getCurrentSet();
    }

    private static List<WaypointWorld> getWaypointWorlds(WaypointsManager manager) {
        WaypointWorldContainer rootContainer = manager.getWorldContainer(manager.getAutoRootContainerID());
        return rootContainer.subContainers.values().stream().map(dim -> dim.addWorld(manager.getAutoWorldID())).toList();
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
        return new ResourceLocation("minimapsync", "xaeros_" + MinimapSync.makeResourceSafeString(icon));
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

    private static void saveWaypoints(WaypointWorld waypointWorld) {
        try {
            AXaeroMinimap.INSTANCE.getSettings().saveWaypoints(waypointWorld);
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

        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            return;
        }

        MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers = session.getMultiTextureRenderTypeRenderers();
        MultiTextureRenderTypeRenderer renderer = multiTextureRenderTypeRenderers.getRenderer(textureId -> RenderSystem.setShaderTexture(0, textureId), MultiTextureRenderTypeRendererProvider::defaultTextureBind, CustomRenderTypes.GUI_NEAREST);

        int textureId = Minecraft.getInstance().getTextureManager().getTexture(getIconResourceLocation(icon)).getId();
        BufferBuilder buffer = renderer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX, textureId);
        Matrix4f pose = matrixStack.last().pose();

        buffer.vertex(pose, x, y, 0).color(r, g, b, a).uv(0, 0).endVertex();
        buffer.vertex(pose, x, y + height, 0).color(r, g, b, a).uv(0, 1).endVertex();
        buffer.vertex(pose, x + width, y + height, 0).color(r, g, b, a).uv(1, 1).endVertex();
        buffer.vertex(pose, x + width, y, 0).color(r, g, b, a).uv(1, 0).endVertex();

        multiTextureRenderTypeRenderers.draw(renderer);
    }
}
