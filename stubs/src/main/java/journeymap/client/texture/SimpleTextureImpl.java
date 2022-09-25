package journeymap.client.texture;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;

public class SimpleTextureImpl extends SimpleTexture implements Texture {
    public SimpleTextureImpl(ResourceLocation location) {
        super(location);
    }

    @Override
    public void remove() {
    }
}
