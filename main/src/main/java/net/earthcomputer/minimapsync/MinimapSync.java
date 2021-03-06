package net.earthcomputer.minimapsync;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class MinimapSync implements ModInitializer {
    public static final ResourceLocation INIT_MODEL = new ResourceLocation("minimapsync:init_model");
    public static final ResourceLocation ADD_WAYPOINT = new ResourceLocation("minimapsync:add_waypoint");
    public static final ResourceLocation REMOVE_WAYPOINT = new ResourceLocation("minimapsync:remove_waypoint");
    public static final ResourceLocation SET_WAYPOINT_DIMENSIONS = new ResourceLocation("minimapsync:set_waypoint_dimensions");
    public static final ResourceLocation SET_WAYPOINT_POS = new ResourceLocation("minimapsync:set_waypoint_pos");
    public static final ResourceLocation SET_WAYPOINT_COLOR = new ResourceLocation("minimapsync:set_waypoint_color");
    public static final ResourceLocation SET_WAYPOINT_DESCRIPTION = new ResourceLocation("minimapsync:set_waypoint_description");
    public static final ResourceLocation SET_WAYPOINT_TELEPORT_RULE = new ResourceLocation("minimapsync:set_waypoint_teleport_rule");
    public static final ResourceLocation TELEPORT = new ResourceLocation("minimapsync:teleport");


    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            WaypointCommand.register(dispatcher);
        });
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(INIT_MODEL)) {
                server.execute(() -> {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    Model.get(server).toPacket(buf);
                    sender.sendPacket(INIT_MODEL, buf);
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ADD_WAYPOINT, (server, player, handler, buf, responseSender) -> {
            Waypoint waypoint = new Waypoint(buf);
            server.execute(() -> addWaypoint(player, server, waypoint.withAuthor(player.getUUID()).withAuthorName(player.getGameProfile().getName())));
        });
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_WAYPOINT, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            server.execute(() -> delWaypoint(player, server, name));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_DIMENSIONS, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            Set<ResourceKey<Level>> dimensions = FriendlyByteBufUtil.readCollection(buf, LinkedHashSet::new, buf1 -> FriendlyByteBufUtil.readResourceKey(buf1, Registry.DIMENSION_REGISTRY));
            server.execute(() -> setWaypointDimensions(player, server, name, dimensions));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_POS, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> setWaypointPos(player, server, name, pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(SET_WAYPOINT_COLOR, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            int color = buf.readInt();
            server.execute(() -> setWaypointColor(player, server, name, color));
        });
        ServerPlayNetworking.registerGlobalReceiver(TELEPORT, (server, player, handler, buf, responseSender) -> {
            String name = buf.readUtf(256);
            ResourceLocation dimensionId = FriendlyByteBufUtil.readNullable(buf, FriendlyByteBuf::readResourceLocation);
            server.execute(() -> {
                if (Model.get(server).teleportRule().canTeleport(player)) {
                    ServerLevel level = dimensionId == null
                        ? (ServerLevel) player.level
                        : server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId));
                    if (level != null) {
                        try {
                            teleportToWaypoint(server, player, name, level);
                        } catch (CommandSyntaxException ignore) {
                        }
                    }
                }
            });
        });
    }

    public static Component createComponent(@Language("JSON") String json, Object... args) {
        return Objects.requireNonNull(Component.Serializer.fromJson(json.formatted(args)));
    }

    public static boolean addWaypoint(@Nullable ServerPlayer source, MinecraftServer server, Waypoint waypoint) {
        Model model = Model.get(server);
        if (!model.waypoints().addWaypoint(waypoint)) {
            return false;
        }
        model.save(server);

        FriendlyByteBuf buf = PacketByteBufs.create();
        waypoint.toPacket(buf);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(ADD_WAYPOINT, buf);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, ADD_WAYPOINT)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static boolean delWaypoint(@Nullable ServerPlayer source, MinecraftServer server, String name) {
        Model model = Model.get(server);
        if (!model.waypoints().removeWaypoint(name)) {
            return false;
        }
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

    private boolean setWaypointDimensions(@Nullable ServerPlayer source, MinecraftServer server, String name, Set<ResourceKey<Level>> dimensions) {
        Model model = Model.get(server);
        if (!model.waypoints().setWaypointDimensions(name, dimensions)) {
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

    public static boolean setWaypointPos(@Nullable ServerPlayer source, MinecraftServer server, String name, BlockPos pos) {
        Model model = Model.get(server);
        if (!model.waypoints().setPos(name, pos)) {
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

    public static boolean setWaypointColor(@Nullable ServerPlayer source, MinecraftServer server, String name, int color) {
        Model model = Model.get(server);
        if (!model.waypoints().setColor(name, color)) {
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

    public static boolean setDescription(MinecraftServer server, String name, @Nullable String description) {
        Model model = Model.get(server);
        if (!model.waypoints().setDescription(name, description)) {
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

    public static boolean teleportToWaypoint(MinecraftServer server, Entity entity, String name, ServerLevel dimension) throws CommandSyntaxException {
        Waypoint waypoint = Model.get(server).waypoints().getWaypoint(name);
        if (waypoint == null) {
            return false;
        }

        double scale = 1 / dimension.dimensionType().coordinateScale();

        TeleportCommand.performTeleport(
            entity.createCommandSourceStack(),
            entity,
            dimension,
            waypoint.pos().getX() * scale + 0.5,
            waypoint.pos().getY(),
            waypoint.pos().getZ() * scale + 0.5,
            Collections.emptySet(),
            entity.getYRot(),
            entity.getXRot(),
            null
        );

        return true;
    }
}
