package net.earthcomputer.minimapsync;

import com.google.common.hash.Hashing;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.ArrayUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MinimapSync implements ModInitializer {
    public static final int CURRENT_PROTOCOL_VERSION = 3;
    public static final ResourceLocation PROTOCOL_VERSION = new ResourceLocation("minimapsync:protocol_version");
    public static final ResourceLocation INIT_MODEL = new ResourceLocation("minimapsync:init_model");
    public static final ResourceLocation ADD_WAYPOINT = new ResourceLocation("minimapsync:add_waypoint");
    public static final ResourceLocation REMOVE_WAYPOINT = new ResourceLocation("minimapsync:remove_waypoint");
    public static final ResourceLocation SET_WAYPOINT_DIMENSIONS = new ResourceLocation("minimapsync:set_waypoint_dimensions");
    public static final ResourceLocation SET_WAYPOINT_POS = new ResourceLocation("minimapsync:set_waypoint_pos");
    public static final ResourceLocation SET_WAYPOINT_COLOR = new ResourceLocation("minimapsync:set_waypoint_color");
    public static final ResourceLocation SET_WAYPOINT_DESCRIPTION = new ResourceLocation("minimapsync:set_waypoint_description");
    public static final ResourceLocation SET_WAYPOINT_TELEPORT_RULE = new ResourceLocation("minimapsync:set_waypoint_teleport_rule");
    public static final ResourceLocation TELEPORT = new ResourceLocation("minimapsync:teleport");
    public static final ResourceLocation ADD_ICON = new ResourceLocation("minimapsync:add_icon");
    public static final ResourceLocation REMOVE_ICON = new ResourceLocation("minimapsync:remove_icon");
    public static final ResourceLocation SET_ICON = new ResourceLocation("minimapsync:set_icon");

    private static final RateLimiter addWaypointLimiter = new RateLimiter(2000, Component.literal("You are adding waypoints too quickly"));
    private static final RateLimiter deleteWaypointLimiter = new RateLimiter(1000, Component.literal("You are deleting waypoints too quickly"));

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            WaypointCommand.register(dispatcher);
        });
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            if (ServerPlayNetworking.canSend(handler, PROTOCOL_VERSION)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(CURRENT_PROTOCOL_VERSION);
                ServerPlayNetworking.send(handler.player, PROTOCOL_VERSION, buf);
            } else if (ServerPlayNetworking.canSend(handler, INIT_MODEL)) {
                server.execute(() -> {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    Model.get(server).toPacket(handler.player.getUUID(), 0, buf);
                    ServerPlayNetworking.send(handler.player, INIT_MODEL, buf);
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(PROTOCOL_VERSION, (server, player, handler, buf, responseSender) -> {
            ((IHasProtocolVersion) handler).minimapsync_setProtocolVersion(Math.min(CURRENT_PROTOCOL_VERSION, buf.readVarInt()));
            if (ServerPlayNetworking.canSend(handler, INIT_MODEL)) {
                server.execute(() -> {
                    FriendlyByteBuf buf2 = PacketByteBufs.create();
                    Model.get(server).toPacket(player.getUUID(), getProtocolVersion(handler), buf2);
                    responseSender.sendPacket(INIT_MODEL, buf2);
                });
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ADD_WAYPOINT, (server, player, handler, buf, responseSender) -> {
            Waypoint waypoint = new Waypoint(getProtocolVersion(handler), buf).withCreationTime(System.currentTimeMillis());
            server.execute(() -> addWaypoint(player, server, waypoint.withAuthor(player.getUUID()).withAuthorName(player.getGameProfile().getName())));
        });
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_WAYPOINT, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            server.execute(() -> delWaypoint(player, player, server, name));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_DIMENSIONS, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            Set<ResourceKey<Level>> dimensions = FriendlyByteBufUtil.readCollection(buf, LinkedHashSet::new, buf1 -> FriendlyByteBufUtil.readResourceKey(buf1, Registries.DIMENSION));
            server.execute(() -> setWaypointDimensions(player, player, server, name, dimensions));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_POS, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> setWaypointPos(player, player, server, name, pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_COLOR, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            int color = buf.readInt();
            server.execute(() -> setWaypointColor(player, player, server, name, color));
        });
        ServerPlayNetworking.registerGlobalReceiver(TELEPORT, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            ResourceLocation dimensionId = FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readResourceLocation);
            server.execute(() -> {
                if (Model.get(server).teleportRule().canTeleport(player)) {
                    ServerLevel level = dimensionId == null
                        ? player.serverLevel()
                        : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
                    if (level != null) {
                        try {
                            teleportToWaypoint(server, player, name, level);
                        } catch (CommandSyntaxException ignore) {
                        }
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ADD_ICON, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            byte[] icon = buf.readByteArray();
            // validate icon format
            try {
                ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(icon));
                Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);
                if (!imageReaders.hasNext()) {
                    return;
                }
                ImageReader imageReader = imageReaders.next();
                ImageReaderSpi originatingProvider = imageReader.getOriginatingProvider();
                if (originatingProvider == null) {
                    return;
                }
                if (!ArrayUtils.contains(originatingProvider.getFormatNames(), "PNG")) {
                    return;
                }
                imageReader.setInput(iis);
                int width = imageReader.getWidth(0);
                int height = imageReader.getHeight(0);
                if (width != height
                    || width < Waypoint.MIN_ICON_DIMENSIONS
                    || width > Waypoint.MAX_ICON_DIMENSIONS
                    || !Mth.isPowerOfTwo(width)) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
            server.execute(() -> addIcon(server, name, icon));
        });
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_ICON, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            server.execute(() -> delIcon(server, name));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_ICON, (server, player, handler, buf, responseSender) -> {
            String waypoint = buf.readUtf(256);
            String icon = FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readUtf);
            server.execute(() -> setWaypointIcon(server, player, waypoint, icon));
        });
    }

    public static int getProtocolVersion(ServerGamePacketListenerImpl handler) {
        return ((IHasProtocolVersion) handler).minimapsync_getProtocolVersion();
    }

    public static int randomColor() {
        // generate color with random hue
        return Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 1, 1);
    }

    public static Component createComponent(@Language("JSON") String json, Object... args) {
        return Objects.requireNonNull(Component.Serializer.fromJson(json.formatted(args)));
    }

    public static boolean addWaypoint(@Nullable ServerPlayer source, MinecraftServer server, Waypoint waypoint) {
        if (source != null && !addWaypointLimiter.checkRateLimit(source, () -> delWaypoint(null, null, server, waypoint.name()))) {
            if (ServerPlayNetworking.canSend(source, REMOVE_WAYPOINT)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUtf(waypoint.name(), 256);
                ServerPlayNetworking.send(source, REMOVE_WAYPOINT, buf);
            }
            return false;
        }

        Model model = Model.get(server);
        if (!model.waypoints().addWaypoint(waypoint)) {
            if (source != null) {
                source.sendSystemMessage(createComponent("""
                    {"text": "Waypoint already exists", "color": "red"}
                """));
                if (ServerPlayNetworking.canSend(source, REMOVE_WAYPOINT)) {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeUtf(waypoint.name(), 256);
                    ServerPlayNetworking.send(source, REMOVE_WAYPOINT, buf);
                }
            }
            return false;
        }
        model.save(server);

        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, ADD_WAYPOINT) && waypoint.isVisibleTo(player)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                waypoint.toPacket(getProtocolVersion(player.connection), buf);
                ServerPlayNetworking.send(player, ADD_WAYPOINT, buf);
            }
        }

        return true;
    }

    public static boolean delWaypoint(@Nullable ServerPlayer source, @Nullable ServerPlayer permissionCheck, MinecraftServer server, String name) {
        Model model = Model.get(server);
        Waypoint existingWaypoint = model.waypoints().getWaypoint(name);
        if (existingWaypoint == null || !existingWaypoint.isVisibleTo(permissionCheck)) {
            return false;
        }

        if (source != null && !deleteWaypointLimiter.checkRateLimit(source, () -> addWaypoint(null, server, existingWaypoint))) {
            if (ServerPlayNetworking.canSend(source, ADD_WAYPOINT)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                existingWaypoint.toPacket(getProtocolVersion(source.connection), buf);
                ServerPlayNetworking.send(source, ADD_WAYPOINT, buf);
            }
            return false;
        }

        model.waypoints().removeWaypoint(permissionCheck, name);
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(REMOVE_WAYPOINT, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, REMOVE_WAYPOINT)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    private boolean setWaypointDimensions(@Nullable ServerPlayer source, @Nullable ServerPlayer permissionCheck, MinecraftServer server, String name, Set<ResourceKey<Level>> dimensions) {
        Model model = Model.get(server);
        if (!model.waypoints().setWaypointDimensions(permissionCheck, name, dimensions)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        FriendlyByteBufUtil.writeCollection(buf, dimensions, FriendlyByteBufUtil::writeResourceKey);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_WAYPOINT_DIMENSIONS, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SET_WAYPOINT_DIMENSIONS)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean setWaypointPos(@Nullable ServerPlayer source, @Nullable ServerPlayer permissionCheck, MinecraftServer server, String name, BlockPos pos) {
        Model model = Model.get(server);
        if (!model.waypoints().setPos(permissionCheck, name, pos)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        buf.writeBlockPos(pos);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_WAYPOINT_POS, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SET_WAYPOINT_POS)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean setWaypointColor(@Nullable ServerPlayer source, @Nullable ServerPlayer permissionCheck, MinecraftServer server, String name, int color) {
        Model model = Model.get(server);
        if (!model.waypoints().setColor(permissionCheck, name, color)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        buf.writeInt(color);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_WAYPOINT_COLOR, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SET_WAYPOINT_COLOR)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean setDescription(MinecraftServer server, @Nullable ServerPlayer permissionCheck, String name, @Nullable String description) {
        Model model = Model.get(server);
        if (!model.waypoints().setDescription(permissionCheck, name, description)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        FriendlyByteBufUtil.writeNullable(buf, description, FriendlyByteBuf::writeUtf);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_WAYPOINT_DESCRIPTION, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SET_WAYPOINT_DESCRIPTION)) {
                player.connection.send(packet);
            }
        }
        return false;
    }

    public static void setTeleportRule(MinecraftServer server, WaypointTeleportRule rule) {
        Model model = Model.get(server).withTeleportRule(rule);
        Model.set(server, model);
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeEnum(rule);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_WAYPOINT_TELEPORT_RULE, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SET_WAYPOINT_TELEPORT_RULE)) {
                player.connection.send(packet);
            }
        }
    }

    public static boolean teleportToWaypoint(MinecraftServer server, ServerPlayer player, String name, ServerLevel dimension) throws CommandSyntaxException {
        Waypoint waypoint = Model.get(server).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return false;
        }
        if (!waypoint.isVisibleTo(player)) {
            return false;
        }

        double scale = 1 / dimension.dimensionType().coordinateScale();

        TeleportCommand.performTeleport(
            player.createCommandSourceStack(),
            player,
            dimension,
            waypoint.pos().getX() * scale + 0.5,
            waypoint.pos().getY(),
            waypoint.pos().getZ() * scale + 0.5,
            Collections.emptySet(),
            player.getYRot(),
            player.getXRot(),
            null
        );

        return true;
    }

    public static boolean addIcon(MinecraftServer server, String name, byte[] icon) {
        Model model = Model.get(server);
        if (model.icons().names().contains(name)) {
            return false;
        }
        model.icons().put(name, icon);
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        buf.writeByteArray(icon);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(ADD_ICON, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ADD_ICON)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean delIcon(MinecraftServer server, String name) {
        Model model = Model.get(server);
        if (model.icons().remove(name) == null) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name, 256);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(REMOVE_ICON, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, REMOVE_ICON)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean setWaypointIcon(MinecraftServer server, @Nullable ServerPlayer permissionCheck, String waypoint, @Nullable String icon) {
        Model model = Model.get(server);
        if (!model.waypoints().setIcon(permissionCheck, waypoint, icon)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(waypoint, 256);
        FriendlyByteBufUtil.writeNullable(buf, icon, FriendlyByteBuf::writeUtf);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(SET_ICON, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SET_ICON)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static String makeFileSafeString(String original) {
        //noinspection UnstableApiUsage,deprecation
        String hash = Hashing.sha1().hashUnencodedChars(original).toString();
        for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            original = original.replace(c, '_');
        }
        original = original.replace('.', '_');
        return original + "_" + hash;
    }

    public static String makeResourceSafeString(String original) {
        //noinspection UnstableApiUsage,deprecation
        String hash = Hashing.sha1().hashUnencodedChars(original).toString();
        original = original.toLowerCase(Locale.ROOT);
        original = Util.sanitizeName(original, ResourceLocation::isAllowedInResourceLocation);
        return original + "/" + hash;
    }
}
