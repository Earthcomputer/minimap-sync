package net.earthcomputer.minimapsync;

import com.demonwav.mcdev.annotations.Translatable;
import com.google.common.hash.Hashing;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.earthcomputer.minimapsync.ducks.IHasPacketSplitterSendableChannels;
import net.earthcomputer.minimapsync.ducks.IHasProtocolVersion;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.earthcomputer.minimapsync.model.WaypointTeleportRule;
import net.earthcomputer.minimapsync.network.AddIconPayload;
import net.earthcomputer.minimapsync.network.AddWaypointPayload;
import net.earthcomputer.minimapsync.network.InitModelPayload;
import net.earthcomputer.minimapsync.network.PacketSplitter;
import net.earthcomputer.minimapsync.network.PacketSplitterRegisterChannelsPayload;
import net.earthcomputer.minimapsync.network.PacketSplitterRegisterChannelsTask;
import net.earthcomputer.minimapsync.network.ProtocolVersionPayload;
import net.earthcomputer.minimapsync.network.ProtocolVersionTask;
import net.earthcomputer.minimapsync.network.RemoveIconPayload;
import net.earthcomputer.minimapsync.network.RemoveWaypointPayload;
import net.earthcomputer.minimapsync.network.SetWaypointColorPayload;
import net.earthcomputer.minimapsync.network.SetWaypointDescriptionPayload;
import net.earthcomputer.minimapsync.network.SetWaypointDimensionsPayload;
import net.earthcomputer.minimapsync.network.SetWaypointIconPayload;
import net.earthcomputer.minimapsync.network.SetWaypointPosPayload;
import net.earthcomputer.minimapsync.network.SetWaypointTeleportRulePayload;
import net.earthcomputer.minimapsync.network.SplitPacketPayload;
import net.earthcomputer.minimapsync.network.TeleportPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.ArrayUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MinimapSync implements ModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MINIMUM_PROTOCOL_VERSION = 5;
    public static final int CURRENT_PROTOCOL_VERSION = 5;

    private static final Map<String, String> defaultTranslations = Util.make(new HashMap<>(), map -> {
        try (BufferedReader reader = Files.newBufferedReader(FabricLoader.getInstance().getModContainer("minimapsync").orElseThrow().findPath("assets/minimapsync/lang/en_us.json").orElseThrow())) {
            JsonReader json = new JsonReader(reader);
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                String value = json.nextString();
                map.put(key, value);
            }
            json.endObject();
        } catch (IOException e) {
            LOGGER.error("Failed to read default translations", e);
        }
    });

    private static final RateLimiter addWaypointLimiter = new RateLimiter(2000, translatableWithFallback("minimapsync.rate_limit.add_waypoint"));
    private static final RateLimiter deleteWaypointLimiter = new RateLimiter(1000, translatableWithFallback("minimapsync.rate_limit.delete_waypoint"));

    public static MutableComponent translatableWithFallback(@Translatable(foldMethod = true) String key, Object... args) {
        return Component.translatableWithFallback(key, defaultTranslations.getOrDefault(key, key), args);
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            WaypointCommand.register(dispatcher);
        });

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, ProtocolVersionPayload.TYPE)) {
                handler.addTask(ProtocolVersionTask.INSTANCE);
            }
            if (ServerConfigurationNetworking.canSend(handler, PacketSplitterRegisterChannelsPayload.TYPE)) {
                handler.addTask(PacketSplitterRegisterChannelsTask.INSTANCE);
            }
        });
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            if (getProtocolVersion(handler) < MINIMUM_PROTOCOL_VERSION) {
                handler.disconnect(translatableWithFallback("minimapsync.disconnect.client_outdated", getProtocolVersion(handler), CURRENT_PROTOCOL_VERSION));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                if (PacketSplitter.get(handler).canSend(InitModelPayload.TYPE)) {
                    PacketSplitter.get(handler).send(new InitModelPayload(Model.get(server).withFormatVersion(getProtocolVersion(handler)).filterForPlayer(handler.player.getUUID())));
                }
            });
        });

        PayloadTypeRegistry.configurationC2S().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(ProtocolVersionPayload.TYPE, (payload, context) -> {
            if (payload.protocolVersion() < MINIMUM_PROTOCOL_VERSION) {
                context.responseSender().disconnect(translatableWithFallback("minimapsync.disconnect.client_outdated", payload.protocolVersion(), MINIMUM_PROTOCOL_VERSION));
                return;
            }
            ((IHasProtocolVersion) context.networkHandler()).minimapsync_setProtocolVersion(Math.min(CURRENT_PROTOCOL_VERSION, payload.protocolVersion()));
            context.networkHandler().completeTask(ProtocolVersionTask.TYPE);
        });

        PayloadTypeRegistry.configurationC2S().register(PacketSplitterRegisterChannelsPayload.TYPE, PacketSplitterRegisterChannelsPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(PacketSplitterRegisterChannelsPayload.TYPE, PacketSplitterRegisterChannelsPayload.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(PacketSplitterRegisterChannelsPayload.TYPE, (payload, context) -> {
            ((IHasPacketSplitterSendableChannels) context.networkHandler()).minimapsync_setPacketSplitterSendableChannels(payload.channels());
            context.networkHandler().completeTask(PacketSplitterRegisterChannelsTask.TYPE);
        });

        PayloadTypeRegistry.playC2S().register(SplitPacketPayload.TYPE, SplitPacketPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SplitPacketPayload.TYPE, SplitPacketPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SplitPacketPayload.TYPE, (payload, context) -> PacketSplitter.get(context.player().connection).receive(payload, context));

        PacketSplitter.register(InitModelPayload.TYPE, InitModelPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(AddWaypointPayload.TYPE, AddWaypointPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AddWaypointPayload.TYPE, AddWaypointPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AddWaypointPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Waypoint waypoint = payload.waypoint()
                .withCreationTime(System.currentTimeMillis())
                .withAuthor(player.getUUID())
                .withAuthorName(player.getGameProfile().getName());
            addWaypoint(player, context.server(), waypoint);
        });

        PayloadTypeRegistry.playC2S().register(RemoveWaypointPayload.TYPE, RemoveWaypointPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveWaypointPayload.TYPE, RemoveWaypointPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RemoveWaypointPayload.TYPE, (payload, context) -> {
            delWaypoint(context.player(), context.player(), context.server(), payload.name());
        });

        PayloadTypeRegistry.playC2S().register(SetWaypointDimensionsPayload.TYPE, SetWaypointDimensionsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SetWaypointDimensionsPayload.TYPE, SetWaypointDimensionsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetWaypointDimensionsPayload.TYPE, (payload, context) -> {
            setWaypointDimensions(context.player(), context.player(), context.server(), payload.name(), payload.dimensions());
        });

        PayloadTypeRegistry.playC2S().register(SetWaypointPosPayload.TYPE, SetWaypointPosPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SetWaypointPosPayload.TYPE, SetWaypointPosPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetWaypointPosPayload.TYPE, (payload, context) -> {
            setWaypointPos(context.player(), context.player(), context.server(), payload.name(), payload.pos());
        });

        PayloadTypeRegistry.playC2S().register(SetWaypointColorPayload.TYPE, SetWaypointColorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SetWaypointColorPayload.TYPE, SetWaypointColorPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetWaypointColorPayload.TYPE, (payload, context) -> {
            setWaypointColor(context.player(), context.player(), context.server(), payload.name(), payload.color());
        });

        PayloadTypeRegistry.playS2C().register(SetWaypointDescriptionPayload.TYPE, SetWaypointDescriptionPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SetWaypointTeleportRulePayload.TYPE, SetWaypointTeleportRulePayload.CODEC);

        PayloadTypeRegistry.playC2S().register(TeleportPayload.TYPE, TeleportPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TeleportPayload.TYPE, (payload, context) -> {
            if (Model.get(context.server()).teleportRule().canTeleport(context.player())) {
                ServerLevel level = payload.dimension() == null
                    ? context.player().serverLevel()
                    : context.server().getLevel(payload.dimension());
                if (level != null) {
                    try {
                        teleportToWaypoint(context.server(), context.player(), payload.name(), level);
                    } catch (CommandSyntaxException ignore) {
                    }
                }
            }
        });

        PacketSplitter.register(AddIconPayload.TYPE, AddIconPayload.CODEC);
        PacketSplitter.registerServerboundHandler(AddIconPayload.TYPE, (payload, context) -> {
            // validate icon format
            try {
                ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(payload.icon()));
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
            addIcon(context.server(), payload.name(), payload.icon());
        });

        PayloadTypeRegistry.playC2S().register(RemoveIconPayload.TYPE, RemoveIconPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveIconPayload.TYPE, RemoveIconPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RemoveIconPayload.TYPE, (payload, context) -> {
            delIcon(context.server(), payload.name());
        });

        PayloadTypeRegistry.playC2S().register(SetWaypointIconPayload.TYPE, SetWaypointIconPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SetWaypointIconPayload.TYPE, SetWaypointIconPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetWaypointIconPayload.TYPE, (payload, context) -> {
            setWaypointIcon(context.server(), context.player(), payload.waypoint(), payload.icon());
        });
    }

    public static int getProtocolVersion(ServerCommonPacketListenerImpl handler) {
        return ((IHasProtocolVersion) handler).minimapsync_getProtocolVersion();
    }

    public static int randomColor() {
        // generate color with random hue
        return Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 1, 1);
    }

    public static Component createComponent(HolderLookup.Provider registries, @Language("JSON") String json, Object... args) {
        return Objects.requireNonNull(Component.Serializer.fromJson(json.formatted(args), registries));
    }

    public static boolean addWaypoint(@Nullable ServerPlayer source, MinecraftServer server, Waypoint waypoint) {
        if (source != null && !addWaypointLimiter.checkRateLimit(source, () -> delWaypoint(null, null, server, waypoint.name()))) {
            if (ServerPlayNetworking.canSend(source, RemoveWaypointPayload.TYPE)) {
                ServerPlayNetworking.send(source, new RemoveWaypointPayload(waypoint.name()));
            }
            return false;
        }

        Model model = Model.get(server);
        if (!model.waypoints().addWaypoint(waypoint)) {
            if (source != null) {
                source.sendSystemMessage(createComponent(server.registryAccess(), """
                    {"text": "Waypoint already exists", "color": "red"}
                """));
                if (ServerPlayNetworking.canSend(source, RemoveWaypointPayload.TYPE)) {
                    ServerPlayNetworking.send(source, new RemoveWaypointPayload(waypoint.name()));
                }
            }
            return false;
        }
        model.save(server);

        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, AddWaypointPayload.TYPE) && waypoint.isVisibleTo(player)) {
                ServerPlayNetworking.send(player, new AddWaypointPayload(waypoint));
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
            if (ServerPlayNetworking.canSend(source, AddWaypointPayload.TYPE)) {
                ServerPlayNetworking.send(source, new AddWaypointPayload(existingWaypoint));
            }
            return false;
        }

        model.waypoints().removeWaypoint(permissionCheck, name);
        model.save(server);

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new RemoveWaypointPayload(name));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, RemoveWaypointPayload.TYPE)) {
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointDimensionsPayload(name, dimensions));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SetWaypointDimensionsPayload.TYPE)) {
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointPosPayload(name, pos));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SetWaypointPosPayload.TYPE)) {
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointColorPayload(name, color));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != source && ServerPlayNetworking.canSend(player, SetWaypointColorPayload.TYPE)) {
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointDescriptionPayload(name, description));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SetWaypointDescriptionPayload.TYPE)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static void setTeleportRule(MinecraftServer server, WaypointTeleportRule rule) {
        Model model = Model.get(server).withTeleportRule(rule);
        Model.set(server, model);
        model.save(server);

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointTeleportRulePayload(rule));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SetWaypointTeleportRulePayload.TYPE)) {
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

        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (PacketSplitter.get(player.connection).canSend(AddIconPayload.TYPE)) {
                PacketSplitter.get(player.connection).send(new AddIconPayload(name, icon));
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new RemoveIconPayload(name));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, RemoveIconPayload.TYPE)) {
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

        Packet<?> packet = ServerPlayNetworking.createS2CPacket(new SetWaypointIconPayload(waypoint, icon));
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SetWaypointIconPayload.TYPE)) {
                player.connection.send(packet);
            }
        }

        return true;
    }

    public static String makeFileSafeString(String original) {
        //noinspection deprecation
        String hash = Hashing.sha1().hashUnencodedChars(original).toString();
        for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            original = original.replace(c, '_');
        }
        original = original.replace('.', '_');
        return original + "_" + hash;
    }

    public static String makeResourceSafeString(String original) {
        //noinspection deprecation
        String hash = Hashing.sha1().hashUnencodedChars(original).toString();
        original = original.toLowerCase(Locale.ROOT);
        original = Util.sanitizeName(original, ResourceLocation::validPathChar);
        return original + "/" + hash;
    }
}
