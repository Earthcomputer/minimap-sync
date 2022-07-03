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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.apache.commons.lang3.StringEscapeUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;
import static net.minecraft.commands.arguments.GameProfileArgument.*;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.*;

public class WaypointCommand {
    private static final DynamicCommandExceptionType DUPLICATE_NAME_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("Duplicate waypoint name: " + name));
    private static final DynamicCommandExceptionType NO_SUCH_WAYPOINT_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("No such waypoint: " + name));
    private static final SimpleCommandExceptionType NO_WAYPOINTS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No waypoints found"));
    private static final SimpleCommandExceptionType CANNOT_TELEPORT_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No permission to teleport"));
    private static final SimpleCommandExceptionType DIMENSION_NO_LONGER_EXISTS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Dimension no longer exists"));

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
                    .executes(ctx -> tpWaypoint(ctx.getSource(), getString(ctx, "name")))))
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
            source.getLevel().dimension(),
            pos,
            uuid
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
            {"text": "=== List of current waypoints ===", "color": "red", "bold": "true"}
        """), false);
        Entity entity = source.getEntity();
        boolean canTeleport = entity instanceof ServerPlayer player && model.teleportRule().canTeleport(player);

        Map<UUID, String> playerNames = new ConcurrentHashMap<>();
        CompletableFuture.allOf(waypoints.stream().map(Waypoint::author).filter(Objects::nonNull).distinct().map(uuid -> {
            CompletableFuture<Void> profile = new CompletableFuture<>();
            SkullBlockEntity.updateGameprofile(new GameProfile(uuid, null), filledProfile -> {
                playerNames.put(uuid, filledProfile.getName());
                profile.complete(null);
            });
            return profile;
        }).toArray(CompletableFuture[]::new)).whenComplete((v, error) -> source.getServer().execute(() -> {
            for (Waypoint waypoint : waypoints) {
                @Language("JSON") String authorStr;
                if (waypoint.author() == null) {
                    authorStr = "";
                } else {
                    authorStr = """
                        [
                            " by ",
                            {"text": "%s", "color": "gray"}
                        ]
                    """;
                    authorStr = authorStr.formatted(StringEscapeUtils.escapeJson(playerNames.get(waypoint.author())));
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
                            {"text": "%s", "color": "#%06x"},
                            {"text": " %d %d %d"}
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
        }));

        return Command.SINGLE_SUCCESS;
    }

    private static int tpWaypoint(CommandSourceStack source, String name) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        MinecraftServer server = source.getServer();
        Model model = Model.get(server);
        if (!(entity instanceof ServerPlayer player) || !model.teleportRule().canTeleport(player)) {
            throw CANNOT_TELEPORT_EXCEPTION.create();
        }
        Waypoint waypoint = model.waypoints().getWaypoint(name);
        if (waypoint == null) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        ServerLevel level = server.getLevel(waypoint.dimension());
        if (level == null) {
            throw DIMENSION_NO_LONGER_EXISTS_EXCEPTION.create();
        }
        TeleportCommand.performTeleport(
            source,
            entity,
            level,
            waypoint.pos().getX() + 0.5,
            waypoint.pos().getY(),
            waypoint.pos().getZ() + 0.5,
            Collections.emptySet(),
            entity.getYRot(),
            entity.getXRot(),
            null
        );

        source.sendSuccess(Component.nullToEmpty("Teleported to waypoint: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setTeleportConfig(CommandSourceStack source, WaypointTeleportRule rule) {
        MinimapSync.setTeleportRule(source.getServer(), rule);
        source.sendSuccess(Component.nullToEmpty("Teleport rule set to: " + rule.name()), true);
        return Command.SINGLE_SUCCESS;
    }
}
