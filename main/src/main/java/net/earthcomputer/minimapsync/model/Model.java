package net.earthcomputer.minimapsync.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import net.earthcomputer.minimapsync.ducks.IHasModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
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

public record Model(
        WaypointList waypoints,
        WaypointTeleportRule teleportRule
) {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BlockPos.class, BlockPosSerializer.INSTANCE)
        .registerTypeAdapter(new TypeToken<ResourceKey<Level>>(){}.getType(), new ResourceKeySerializer<>(Registry.DIMENSION_REGISTRY))
        .registerTypeAdapterFactory(new LowerCaseEnumTypeAdapterFactory())
        .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    public Model() {
        this(new WaypointList(), WaypointTeleportRule.NEVER);
    }

    public Model(FriendlyByteBuf buf) {
        this(new WaypointList(buf), buf.readEnum(WaypointTeleportRule.class));
    }

    private static Path getSavePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("minimapsync.json");
    }

    public static Model load(MinecraftServer server) {
        Path file = getSavePath(server);
        try (var reader = Files.newBufferedReader(file)) {
            Model result = GSON.fromJson(reader, Model.class);
            if (result != null) {
                return result;
            }
        } catch (IOException | JsonParseException e) {
            if (Files.exists(file)) {
                LOGGER.error(() -> "Failed to load model file: " + file, e);
            }
        }
        return new Model();
    }

    public void toPacket(FriendlyByteBuf buf) {
        waypoints.toPacket(buf);
        buf.writeEnum(teleportRule);
    }

    public void save(MinecraftServer server) {
        Path file = getSavePath(server);
        Path newFile = file.resolveSibling("minimapsync.json.new");
        try (var writer = Files.newBufferedWriter(newFile)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error(() -> "Failed to save model file: " + newFile, e);
            return;
        }
        Util.safeReplaceFile(file, newFile, file.resolveSibling("minimapsync.json.old"));
    }

    public Model withTeleportRule(WaypointTeleportRule teleportRule) {
        return new Model(waypoints, teleportRule);
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
