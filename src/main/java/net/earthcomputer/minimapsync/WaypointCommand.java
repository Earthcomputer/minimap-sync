package net.earthcomputer.minimapsync;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;
import static net.minecraft.commands.arguments.DimensionArgument.*;
import static net.minecraft.commands.arguments.GameProfileArgument.*;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.*;

public class WaypointCommand {
    private static final long MAX_IMAGE_SIZE = 1024 * 1024; // 1 megabyte
    private static final String MAX_IMAGE_SIZE_STRING = "1MB";

    private static final DynamicCommandExceptionType DUPLICATE_NAME_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("Duplicate waypoint name: " + name));
    private static final DynamicCommandExceptionType NO_SUCH_WAYPOINT_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("No such waypoint: " + name));
    private static final SimpleCommandExceptionType NO_WAYPOINTS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No waypoints found"));
    private static final SimpleCommandExceptionType CANNOT_TELEPORT_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No permission to teleport"));
    private static final SimpleCommandExceptionType NO_ICONS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("No icons found"));
    private static final DynamicCommandExceptionType DUPLICATE_ICON_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("Duplicate icon name: " + name));
    private static final DynamicCommandExceptionType INVALID_URI_EXCEPTION = new DynamicCommandExceptionType(uri -> Component.nullToEmpty("Invalid URI: " + uri));
    private static final DynamicCommandExceptionType INVALID_URI_SCHEME_EXCEPTION = new DynamicCommandExceptionType(scheme -> Component.nullToEmpty("Invalid URI scheme: " + scheme));
    private static final SimpleCommandExceptionType COULDNT_CONNECT_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Couldn't connect to URL"));
    private static final SimpleCommandExceptionType UNKNOWN_HOST_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Couldn't connect to URL: unknown host"));
    private static final Dynamic2CommandExceptionType COULDNT_CONNECT_STATUS_CODE_EXCEPTION = new Dynamic2CommandExceptionType((code, desc) -> Component.nullToEmpty("Couldn't connect to URL: HTTP " + code + " " + desc));
    private static final SimpleCommandExceptionType IMAGE_TOO_LARGE_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Image too large, must be at most " + MAX_IMAGE_SIZE_STRING));
    private static final SimpleCommandExceptionType WRONG_IMAGE_DIMENSIONS_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Wrong image dimensions: expected a square power of 2 between " + Waypoint.MIN_ICON_DIMENSIONS + "x" + Waypoint.MIN_ICON_DIMENSIONS + " and " + Waypoint.MAX_ICON_DIMENSIONS + "x" + Waypoint.MAX_ICON_DIMENSIONS + " pixels"));
    private static final SimpleCommandExceptionType INVALID_IMAGE_FORMAT_EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("Invalid image format"));
    private static final DynamicCommandExceptionType NO_SUCH_ICON_EXCEPTION = new DynamicCommandExceptionType(name -> Component.nullToEmpty("No such icon: " + name));

    private static final SuggestionProvider<CommandSourceStack> WAYPOINT_NAME_SUGGESTOR = (context, builder) -> {
        Model model = Model.get(context.getSource().getServer());
        return suggestMatching(
            model.waypoints().getWaypoints(null).map(Waypoint::name),
            builder
        );
    };
    private static final SuggestionProvider<CommandSourceStack> ICON_NAME_SUGGESTOR = (context, builder) -> {
        Model model = Model.get(context.getSource().getServer());
        return suggestMatching(model.icons().names().stream(), builder);
    };

    private static CompletableFuture<Suggestions> suggestMatching(
        Stream<String> collection,
        SuggestionsBuilder builder
    ) {
        String existingString = builder.getRemaining().toLowerCase(Locale.ROOT);
        collection.forEach(suggestion -> {
            if (!existingString.isEmpty()) {
                char quoteChar = existingString.charAt(0);
                if (StringReader.isQuotedStringStart(quoteChar)) {
                    String escaped = suggestion.replace("\\", "\\\\").replace("" + quoteChar, "\\" + quoteChar);
                    if (SharedSuggestionProvider.matchesSubStr(existingString.substring(1), escaped.toLowerCase(Locale.ROOT))) {
                        builder.suggest(quoteChar + escaped + quoteChar);
                    }
                    return;
                }
            }

            if (!SharedSuggestionProvider.matchesSubStr(existingString, suggestion.toLowerCase(Locale.ROOT))) {
                return;
            }

            boolean needsQuotes = false;
            for (int i = 0; i < suggestion.length(); i++) {
                if (!StringReader.isAllowedInUnquotedString(suggestion.charAt(i))) {
                    needsQuotes = true;
                    break;
                }
            }
            if (needsQuotes) {
                String escaped = suggestion.replace("\\", "\\\\").replace("\"", "\\\"");
                builder.suggest("\"" + escaped + "\"");
            } else {
                builder.suggest(suggestion);
            }
        });
        return builder.buildFuture();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("waypoint")
            .then(literal("reload")
                .executes(ctx -> reloadModel(ctx.getSource())))
            .then(literal("add")
                .then(argument("name", string())
                    .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), BlockPos.containing(ctx.getSource().getPosition()), null))
                    .then(argument("pos", blockPos())
                        .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), getSpawnablePos(ctx, "pos"), null))
                        .then(argument("description", greedyString())
                            .executes(ctx -> addWaypoint(ctx.getSource(), getString(ctx, "name"), getSpawnablePos(ctx, "pos"), getString(ctx, "description")))))))
            .then(literal("del")
                .then(argument("name", string())
                    .suggests(WAYPOINT_NAME_SUGGESTOR)
                    .executes(ctx -> delWaypoint(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("edit")
                .then(argument("name", string())
                    .suggests(WAYPOINT_NAME_SUGGESTOR)
                    .then(argument("description", greedyString())
                        .executes(ctx -> editWaypoint(ctx.getSource(), getString(ctx, "name"), getString(ctx, "description"))))))
            .then(literal("color")
                .then(argument("name", string())
                    .suggests(WAYPOINT_NAME_SUGGESTOR)
                    .then(argument("color", integer(0, 0xffffff))
                        .executes(ctx -> setWaypointColor(ctx.getSource(), getString(ctx, "name"), getInteger(ctx, "color"))))))
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
                    .suggests(WAYPOINT_NAME_SUGGESTOR)
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
                        .executes(ctx -> setTeleportConfig(ctx.getSource(), WaypointTeleportRule.ALWAYS)))))
            .then(literal("icon")
                .then(literal("list")
                    .executes(ctx -> listIcons(ctx.getSource())))
                .then(literal("add")
                    .then(argument("name", string())
                        .then(argument("url", greedyString())
                            .executes(ctx -> addIcon(ctx.getSource(), getString(ctx, "name"), getString(ctx, "url"))))))
                .then(literal("del")
                    .then(argument("name", string())
                        .suggests(ICON_NAME_SUGGESTOR)
                        .executes(ctx -> delIcon(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("set")
                    .then(argument("waypoint", string())
                        .suggests(WAYPOINT_NAME_SUGGESTOR)
                        .then(argument("icon", string())
                            .suggests(ICON_NAME_SUGGESTOR)
                            .executes(ctx -> setWaypointIcon(ctx.getSource(), getString(ctx, "waypoint"), getString(ctx, "icon"))))))
                .then(literal("unset")
                    .then(argument("waypoint", string())
                        .suggests(WAYPOINT_NAME_SUGGESTOR)
                        .executes(ctx -> unsetWaypointIcon(ctx.getSource(), getString(ctx, "waypoint")))))));
    }

    private static int reloadModel(CommandSourceStack source) {
        Model.set(source.getServer(), Model.load(source.getServer()));
        source.sendSuccess(() -> Component.nullToEmpty("Reloaded minimap model"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addWaypoint(CommandSourceStack source, String name, BlockPos pos, @Nullable String description) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        UUID uuid = entity == null ? null : entity.getUUID();

        int color = MinimapSync.randomColor();

        Waypoint waypoint = new Waypoint(
            name,
            description,
            color,
            new LinkedHashSet<>(List.of(source.getLevel().dimension())),
            pos,
            uuid,
            entity instanceof ServerPlayer player ? player.getGameProfile().getName() : null,
            null,
            System.currentTimeMillis()
        );

        if (!MinimapSync.addWaypoint(null, source.getServer(), waypoint)) {
            throw DUPLICATE_NAME_EXCEPTION.create(name);
        }

        source.sendSuccess(() -> Component.nullToEmpty("Waypoint added: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int delWaypoint(CommandSourceStack source, String name) throws CommandSyntaxException {
        if (!MinimapSync.delWaypoint(null, source.getServer(), name)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(() -> Component.nullToEmpty("Waypoint deleted: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int editWaypoint(CommandSourceStack source, String name, String description) throws CommandSyntaxException {
        if (!MinimapSync.setDescription(source.getServer(), name, description)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(() -> Component.nullToEmpty("Waypoint edited: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setWaypointColor(CommandSourceStack source, String name, int color) throws CommandSyntaxException {
        if (!MinimapSync.setWaypointColor(null, source.getServer(), name, color)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(name);
        }

        source.sendSuccess(() -> Component.nullToEmpty("Set waypoint color of " + name + " to " + color), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int listWaypoints(CommandSourceStack source, @Nullable UUID author) throws CommandSyntaxException {
        Model model = Model.get(source.getServer());
        List<Waypoint> waypoints = model.waypoints().getWaypoints(author).toList();
        if (waypoints.isEmpty()) {
            throw NO_WAYPOINTS_EXCEPTION.create();
        }
        source.sendSuccess(() -> MinimapSync.createComponent("""
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
            source.sendSuccess(() -> MinimapSync.createComponent("""
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
                                "value": "/waypoint tp \\"%s\\" %s"
                            }
                        }
                    ]
                """;
                    teleportStr = teleportStr.formatted(StringEscapeUtils.escapeJson(waypoint.name()), dimension.location());
                    teleportStr = "," + teleportStr;
                }
                String authorStr_f = authorStr;
                String teleportStr_f = teleportStr;
                source.sendSuccess(() -> MinimapSync.createComponent("""
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
                    authorStr_f,
                    teleportStr_f
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

        source.sendSuccess(() -> Component.nullToEmpty("Teleported to waypoint: " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setTeleportConfig(CommandSourceStack source, WaypointTeleportRule rule) {
        MinimapSync.setTeleportRule(source.getServer(), rule);
        source.sendSuccess(() -> Component.nullToEmpty("Teleport rule set to: " + rule.name()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listIcons(CommandSourceStack source) throws CommandSyntaxException {
        Model model = Model.get(source.getServer());
        if (model.icons().isEmpty()) {
            throw NO_ICONS_EXCEPTION.create();
        }

        source.sendSuccess(() -> MinimapSync.createComponent("""
            {"text": "=== List of %d icons ===", "color": "aqua", "bold": "true"}
        """, model.icons().size()), false);
        for (String icon : model.icons().names()) {
            source.sendSuccess(() -> MinimapSync.createComponent("""
                [
                    "- ",
                    {"text": "%s", "color": "gold"}
                ]
            """, icon), false);
        }

        return model.icons().size();
    }

    private static int addIcon(CommandSourceStack source, String name, String url) throws CommandSyntaxException {
        Model model = Model.get(source.getServer());
        if (model.icons().names().contains(name)) {
            throw DUPLICATE_ICON_EXCEPTION.create(name);
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw INVALID_URI_EXCEPTION.create(url);
        }

        String scheme = uri.getScheme();
        byte[] data;

        if ("https".equals(scheme)) {
            URL urlObj;
            try {
                urlObj = uri.toURL();
            } catch (MalformedURLException e) {
                throw INVALID_URI_EXCEPTION.create(url);
            }

            HttpURLConnection connection;
            int responseCode;
            String responseMessage;
            try {
                connection = (HttpURLConnection) urlObj.openConnection();
                connection.connect();
                responseCode = connection.getResponseCode();
                responseMessage = connection.getResponseMessage();
            } catch (UnknownHostException e) {
                throw UNKNOWN_HOST_EXCEPTION.create();
            } catch (IOException e) {
                throw COULDNT_CONNECT_EXCEPTION.create();
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw COULDNT_CONNECT_STATUS_CODE_EXCEPTION.create(responseCode, responseMessage);
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_IMAGE_SIZE) {
                throw IMAGE_TOO_LARGE_EXCEPTION.create();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                int amtRead;
                while ((amtRead = in.read(buffer)) != -1) {
                    totalRead += amtRead;
                    if (totalRead > MAX_IMAGE_SIZE) {
                        throw IMAGE_TOO_LARGE_EXCEPTION.create();
                    }
                    baos.write(buffer, 0, amtRead);
                }
            } catch (IOException e) {
                throw COULDNT_CONNECT_EXCEPTION.create();
            }
            data = baos.toByteArray();
        } else if ("data".equals(scheme)) {
            int commaIndex = url.indexOf(',');
            if (commaIndex == -1) {
                throw INVALID_URI_EXCEPTION.create(url);
            }
            try {
                data = Base64.getDecoder().decode(url.substring(commaIndex + 1));
            } catch (IllegalArgumentException e) {
                throw INVALID_URI_EXCEPTION.create(url);
            }
        } else {
            throw INVALID_URI_SCHEME_EXCEPTION.create(scheme);
        }

        // Re-encode the image in PNG format, and check its dimensions while we're at it
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw INVALID_IMAGE_FORMAT_EXCEPTION.create();
        }
        if (image.getWidth() != image.getHeight()
            || image.getWidth() < Waypoint.MIN_ICON_DIMENSIONS
            || image.getWidth() > Waypoint.MAX_ICON_DIMENSIONS
            || !Mth.isPowerOfTwo(image.getWidth())
        ) {
            throw WRONG_IMAGE_DIMENSIONS_EXCEPTION.create();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MinimapSync.addIcon(source.getServer(), name, baos.toByteArray());
        source.sendSuccess(() -> Component.nullToEmpty("Added waypoint icon " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int delIcon(CommandSourceStack source, String name) throws CommandSyntaxException {
        if (!MinimapSync.delIcon(source.getServer(), name)) {
            throw NO_SUCH_ICON_EXCEPTION.create(name);
        }

        source.sendSuccess(() -> Component.nullToEmpty("Deleted waypoint icon " + name), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setWaypointIcon(CommandSourceStack source, String waypoint, String icon) throws CommandSyntaxException {
        Model model = Model.get(source.getServer());
        if (!model.icons().names().contains(icon)) {
            throw NO_SUCH_ICON_EXCEPTION.create(icon);
        }
        if (!MinimapSync.setWaypointIcon(source.getServer(), waypoint, icon)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(waypoint);
        }
        source.sendSuccess(() -> Component.nullToEmpty("Set icon of waypoint " + waypoint + " to " + icon), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unsetWaypointIcon(CommandSourceStack source, String waypoint) throws CommandSyntaxException {
        if (!MinimapSync.setWaypointIcon(source.getServer(), waypoint, null)) {
            throw NO_SUCH_WAYPOINT_EXCEPTION.create(waypoint);
        }
        source.sendSuccess(() -> Component.nullToEmpty("Unset icon of waypoint " + waypoint), true);
        return Command.SINGLE_SUCCESS;
    }
}
