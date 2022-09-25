package journeymap.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class DynamicTextureImpl extends DynamicTexture implements Texture {
    public DynamicTextureImpl(NativeImage image) {
        super(image);
    }

    @Override
    public void remove() {
    }
}
