package net.earthcomputer.minimapsync.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;
import java.util.Base64;

public enum Base64Serializer implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
    INSTANCE;

    @Override
    public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            return Base64.getDecoder().decode(GsonHelper.convertToString(json, "byte[]"));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid base 64 scheme", e);
        }
    }

    @Override
    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(Base64.getEncoder().encodeToString(src));
    }
}
