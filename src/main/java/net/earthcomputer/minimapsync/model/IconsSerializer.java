package net.earthcomputer.minimapsync.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.earthcomputer.minimapsync.MinimapSync;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record IconsSerializer(Path imageFolder) implements JsonSerializer<Icons>, JsonDeserializer<Icons> {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public Icons deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Map<String, byte[]> result = new HashMap<>();
        Set<String> dirty = new HashSet<>();
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                String iconName = entry.getKey();
                String base64Image = GsonHelper.convertToString(entry.getValue(), "icon");
                byte[] image;
                try {
                    image = Base64.getDecoder().decode(base64Image);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Failed to decode invalid base 64 image");
                }
                result.put(iconName, image);
                dirty.add(iconName);
            }
        } else {
            for (JsonElement iconNameElt : GsonHelper.convertToJsonArray(json, "icons")) {
                String iconName = GsonHelper.convertToString(iconNameElt, "icon");
                byte[] image = getImage(iconName);
                if (image != null) {
                    result.put(iconName, image);
                }
            }
        }
        return new Icons(result, dirty);
    }

    private byte @Nullable [] getImage(String iconName) {
        try {
            return Files.readAllBytes(getImageFile(iconName));
        } catch (IOException e) {
            LOGGER.error("Unable to read image file " + getImageFile(iconName) + " for icon " + iconName, e);
            return null;
        }
    }

    @Override
    public JsonElement serialize(Icons src, Type typeOfSrc, JsonSerializationContext context) {
        if (!src.dirty().isEmpty()) {
            try {
                Files.createDirectories(imageFolder);
            } catch (IOException e) {
                LOGGER.error("Failed to ensure image folder " + imageFolder + " exists", e);
                return context.serialize(src.icons().keySet());
            }
        }

        for (String iconName : src.dirty()) {
            byte[] image = src.icons().get(iconName);
            try {
                if (image == null) {
                    Files.deleteIfExists(getImageFile(iconName));
                } else {
                    Files.write(getImageFile(iconName), image);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write image file" + getImageFile(iconName) + " for icon " + iconName, e);
            }
        }
        return context.serialize(src.icons().keySet());
    }

    private Path getImageFile(String iconName) {
        return imageFolder.resolve(MinimapSync.makeFileSafeString(iconName) + ".png");
    }
}
