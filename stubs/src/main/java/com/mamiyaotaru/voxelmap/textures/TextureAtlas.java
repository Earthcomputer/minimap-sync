package com.mamiyaotaru.voxelmap.textures;

import net.minecraft.client.renderer.texture.AbstractTexture;

import java.awt.image.BufferedImage;

public abstract class TextureAtlas extends AbstractTexture {
    public abstract void stitchNew();
    public abstract Sprite registerIconForBufferedImage(String name, BufferedImage image);

    public abstract Sprite getAtlasSprite(String name);

    public abstract Sprite getMissingImage();
}
