package net.earthcomputer.minimapsync;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringEscapeUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;
import static net.minecraft.commands.arguments.DimensionArgument.*;
import static net.minecraft.commands.arguments.GameProfileArgument.*;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.*;

public class WaypointCommand {
    private static final DynamicCommandExceptionType DUPLICATE_NAME_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("Duplicate waypoint name: " + name));
    private static final DynamicCommandExceptionType NO_SUCH_WAYPOINT_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("No such waypoint: " + name));
    private static final SimpleCommandExceptionType NO_WAYPOINTS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No waypoints found"));
    private static final SimpleCommandExceptionType CANNOT_TELEPORT_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No permission to teleport"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("waypoint")
            .then(literal("add")
                .then(argument("name", string())
                    .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), new BlockPos(ctx.getSource().getPosition()), null))
                    .then(argument("pos", blockPos())
                        .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), getSpawnablePos(ctx, "pos"), null))
                        .then(argument("description", greedyString())
                            .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), getSpawnablePos(ctx, "pos"), getString(ctx, "description")))))))
            .then(literal("del")
                .then(argument("name", string())
                    .executes(ctx -> delWaypoint(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("edit")
                .then(argument("name", string())
                    .then(argument("description", greedyString())
                        .executes(ctx -> editWaypoint(ctx.getSource(), getString(ctx, "name"), getString(ctx, "description"))))))
            .then(literal("list")
                .executes(ctx -> listWaypoints(ctx.getSource(), null))
                .then(argument("author", gameProfile())
                    .executes(ctx -> {
                        Collection<GameProfile> profiles = getGameProfiles(ctx, "author");
                        if (profiles.size() != 1) {
                            throw profiles.isEmpty() ? EntityArgument.NO_PLAYERS_FOUND.create() : EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
                        }
                        return listWaypoints(ctx.getSource(), profiles.iterator().next().getId());
                    })))
            .then(literal("tp")
                .then(argument("name", string())
                    .executes(ctx -> tpWaypoint(ctx.getSource(), getString(ctx, "name"), ctx.getSource().getLevel()))
                    .then(argument("dimension", dimension())
                        .executes(ctx -> tpWaypoint(ctx.getSource(), getString(ctx, "name"), getDimension(ctx, "dimension"))))))
            .then(literal("config")
                .then(literal("teleport")
                    .requires(src -> src.hasPermission(2))
                    .then(literal("never")
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.NEVER)))
                    .then(literal("creative_players")
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.CREATIVE_PLAYERS)))
                    .then(literal("creative_and_spectator_players")
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.CREATIVE_AND_SPECTATOR_PLAYERS)))
                    .then(literal("op_players")
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.OP_PLAYERS)))
                    .then(literal("always")
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.ALWAYS))))));
    }

    private static int addWaypoint(CommandSourceStack source, String name, BlockPos pos, @Nullable String description) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        UUID uuid = entity == null ? null : entity.getUUID();

        // generate color with random hue
        int color = Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 1, 1);

        Waypoint waypoint = new Waypoint(
            name,
            description,
            color,
            new LinkedHashSet<>(List.of(source.getLevel().dimension())),
            pos,
            uuid,
            entity instanceof ServerPlayer player ? player.getGameProfile().getName() : null
        );

        if (!MinimapSync.addWaypoint(null, source.getServer(), waypoint)) {
            throw DUPLICATE_NAME_EXCEPTION.create(name);
        }

        source.sendSuccess(Component.nullToEmpty("Waypoint added: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int delWaypoint(CommandSourceStack source, String name) throws CommandSyntaxException {
        if (!MinimapSync.delWaypoint(null, source.getServer(), name)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(Component.nullToEmpty("Waypoint deleted: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int editWaypoint(CommandSourceStack source, String name, String description) throws CommandSyntaxException {
        if (!MinimapSync.setDescription(source.getServer(), name, description)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(Component.nullToEmpty("Waypoint edited: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int listWaypoints(CommandSourceStack source, @Nullable UUID author) throws CommandSyntaxException {
        Model model = Model.get(source.getServer());
        List<Waypoint> waypoints = model.waypoints().getWaypoints(author).toList();
        if (waypoints.isEmpty()) {
            throw NO_WAYPOINTS_EXCEPTION.create();
        }
        source.sendSuccess(MinimapSync.createComponent("""
            {"text": "=== List of current waypoints ===", "color": "aqua", "bold": "true"}
        """), false);
        Entity entity = source.getEntity();
        boolean canTeleport = entity instanceof ServerPlayer player && model.teleportRule().canTeleport(player);

        Map<ResourceKey<Level>, List<Waypoint>> waypointsByDimension = new HashMap<>();
        for (Waypoint waypoint : waypoints) {
            for (ResourceKey<Level> dimension : waypoint.dimensions()) {
                waypointsByDimension.computeIfAbsent(dimension, k -> new ArrayList<>()).add(waypoint);
            }
        }

        waypointsByDimension.forEach((dimension, wpts) -> {
            source.sendSuccess(MinimapSync.createComponent("""
                {"text": "in %s", "color": "green"}
            """, dimension.location()), false);

            for (Waypoint waypoint : wpts) {
                @Language("JSON") String authorStr;
                if (waypoint.authorName() == null) {
                    authorStr = "";
                } else {
                    authorStr = """
                    [
                        {"text": " by ", "color": "gray", "bold": "true"},
                        {"text": "%s", "color": "gray"}
                    ]
                """;
                    authorStr = authorStr.formatted(StringEscapeUtils.escapeJson(waypoint.authorName()));
                    authorStr = "," + authorStr;
                }
                @Language("JSON") String teleportStr;
                if (!canTeleport) {
                    teleportStr = "";
                } else {
                    teleportStr = """
                    [
                        " ",
                        {
                            "text": "[Teleport]",
                            "color": "gold",
                            "clickEvent": {
                                "action": "run_command",
                                "value": "/waypoint tp %s"
                            }
                        }
                    ]
                """;
                    teleportStr = teleportStr.formatted(StringEscapeUtils.escapeJson(waypoint.name()));
                    teleportStr = "," + teleportStr;
                }
                source.sendSuccess(MinimapSync.createComponent("""
                    [
                        "- ",
                        {"text": "%s", "color": "#%06x", "bold": "true"},
                        {"text": ": %d %d %d"}
                        %s%s
                    ]
                """,
                    StringEscapeUtils.escapeJson(waypoint.name()),
                    waypoint.color(),
                    waypoint.pos().getX(),
                    waypoint.pos().getY(),
                    waypoint.pos().getZ(),
                    authorStr,
                    teleportStr
                ), false);
            }
        });

        return waypoints.size();
    }

    private static int tpWaypoint(CommandSourceStack source, String name, ServerLevel dimension) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        MinecraftServer server = source.getServer();
        Model model = Model.get(server);
        if (!(entity instanceof ServerPlayer player) || !model.teleportRule().canTeleport(player)) {
            throw CANNOT_TELEPORT_EXCEPTION.create();
        }

        if (!MinimapSync.teleportToWaypoint(server, entity, name, dimension)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(Component.nullToEmpty("Teleported to waypoint: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setTeleportConfig(CommandSourceStack source, WaypointTeleportRule rule) {
        MinimapSync.setTeleportRule(source.getServer(), rule);
        source.sendSuccess(Component.nullToEmpty("Teleport rule set to: " + rule.name()), true);
        return Command.SINGLE_SUCCESS;
    }
}
