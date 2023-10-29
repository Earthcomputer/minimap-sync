package net.earthcomputer.minimapsync.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

public record ResourceKeySerializer<T>(
    ResourceKey<? extends Registry<T>> registry
) implements JsonSerializer<ResourceKey<T>>, JsonDeserializer<ResourceKey<T>> {
    @Override
    public ResourceKey<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String str = GsonHelper.convertToString(json, "resource key");
        ResourceLocation rl = ResourceLocation.tryParse(str);
        if (rl == null) {
            throw new JsonParseException("Invalid resource key: " + str);
        }
        return ResourceKey.create(registry, rl);
    }

    @Override
    public JsonElement serialize(ResourceKey<T> src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.location().toString());
    }
}
