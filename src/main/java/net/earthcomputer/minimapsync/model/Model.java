package net.earthcomputer.minimapsync.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.earthcomputer.minimapsync.network.MinimapSyncStreamCodecs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.LowerCaseEnumTypeAdapterFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public record Model(
    int formatVersion,
    WaypointList waypoints,
    WaypointTeleportRule teleportRule,
    Icons icons
) {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final StreamCodec<RegistryFriendlyByteBuf, Model> STREAM_CODEC = StreamCodec.composite(
        MinimapSyncStreamCodecs.PROTOCOL_VERSION,
        Model::formatVersion,
        WaypointList.STREAM_CODEC,
        Model::waypoints,
        WaypointTeleportRule.STREAM_CODEC,
        Model::teleportRule,
        Icons.STREAM_CODEC,
        Model::icons,
        Model::new
    );

    private static Gson getGson(MinecraftServer server) {
        return new GsonBuilder()
            .registerTypeAdapter(BlockPos.class, BlockPosSerializer.INSTANCE)
            .registerTypeAdapter(new TypeToken<ResourceKey<Level>>(){}.getType(), new ResourceKeySerializer<>(Registries.DIMENSION))
            .registerTypeAdapterFactory(new LowerCaseEnumTypeAdapterFactory())
            .registerTypeAdapter(Icons.class, new IconsSerializer(server.getWorldPath(LevelResource.ROOT).resolve("minimapsync_icons")))
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    }

    public Model() {
        this(
            MinimapSync.CURRENT_PROTOCOL_VERSION,
            new WaypointList(),
            WaypointTeleportRule.NEVER,
            new Icons()
        );
    }

    public Model filterForPlayer(UUID player) {
        return new Model(formatVersion, waypoints.filterForPlayer(player), teleportRule, icons);
    }

    private static Path getSavePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("minimapsync.json");
    }

    public static Model load(MinecraftServer server) {
        Path file = getSavePath(server);
        try (var reader = Files.newBufferedReader(file)) {
            Model result = getGson(server).fromJson(reader, Model.class);
            if (result != null) {
                return updateLoadedModel(server, result);
            }
        } catch (IOException | JsonParseException e) {
            if (Files.exists(file)) {
                LOGGER.error(() -> "Failed to load model file: " + file, e);
            }
        }
        return new Model();
    }

    /**
     * Datafixes the model format
     */
    private static Model updateLoadedModel(MinecraftServer server, Model model) {
        if (model.formatVersion > 2) {
            return model;
        }

        model.waypoints().setAllToLocalVisibility();

        if (model.formatVersion > 1) {
            return model;
        }

        // added icons field
        if (model.icons == null) {
            model = model.withIcons(new Icons());
        }

        // added waypoint creation time
        model.waypoints().setAllToCurrentTime();
        // important to save the model if creation times have been added
        model.save(server);

        return model;
    }

    public void save(MinecraftServer server) {
        Model model = withFormatVersion(MinimapSync.CURRENT_PROTOCOL_VERSION);
        Path file = getSavePath(server);
        Path newFile = file.resolveSibling("minimapsync.json.new");
        try (var writer = Files.newBufferedWriter(newFile)) {
            getGson(server).toJson(model, writer);
        } catch (IOException e) {
            LOGGER.error(() -> "Failed to save model file: " + newFile, e);
            return;
        }
        Util.safeReplaceFile(file, newFile, file.resolveSibling("minimapsync.json.old"));
    }

    public Model withFormatVersion(int formatVersion) {
        return new Model(formatVersion, waypoints, teleportRule, icons);
    }

    public Model withTeleportRule(WaypointTeleportRule teleportRule) {
        return new Model(formatVersion, waypoints, teleportRule, icons);
    }

    public Model withIcons(Icons icons) {
        return new Model(formatVersion, waypoints, teleportRule, icons);
    }

    public static Model get(MinecraftServer server) {
        return ((IHasModel) server).minimapsync_model();
    }

    public static void set(MinecraftServer server, Model model) {
        ((IHasModel) server).minimapsync_setModel(model);
    }

    @Environment(EnvType.CLIENT)
    public static Model get(ClientPacketListener listener) {
        return ((IHasModel) listener).minimapsync_model();
    }

    @Environment(EnvType.CLIENT)
    public static void set(ClientPacketListener listener, Model model) {
        ((IHasModel) listener).minimapsync_setModel(model);
    }
}
