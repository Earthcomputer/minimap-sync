package net.earthcomputer.minimapsync.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

public enum BlockPosSerializer implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    INSTANCE;

    @Override
    public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray array = GsonHelper.convertToJsonArray(json, "BlockPos");
        if (array.size() != 3) {
            throw new JsonParseException("Expected a BlockPos array of length 3");
        }
        return new BlockPos(
                GsonHelper.convertToInt(array.get(0), "x"),
                GsonHelper.convertToInt(array.get(1), "y"),
                GsonHelper.convertToInt(array.get(2), "z")
        );
    }

    @Override
    public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray(3);
        array.add(src.getX());
        array.add(src.getY());
        array.add(src.getZ());
        return array;
    }
}
