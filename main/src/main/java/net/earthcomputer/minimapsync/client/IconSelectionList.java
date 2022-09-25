package net.earthcomputer.minimapsync.client;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class IconSelectionList extends ObjectSelectionList<IconSelectionList.Entry> {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Screen screen;
    private final Runnable confirmAction;
    private final Set<String> iconsToRemove = new HashSet<>();
    private final Map<String, Path> iconsToAdd = new HashMap<>();

    public IconSelectionList(
        Minecraft minecraft,
        @Nullable Runnable confirmAction,
        Screen screen,
        int width, int height,
        int top, int bottom,
        int itemHeight,
        @Nullable IconSelectionList other
    ) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.screen = screen;
        this.confirmAction = Objects.requireNonNullElseGet(confirmAction, () -> () -> {});
        if (other != null) {
            for (int i = 0; i < other.getItemCount(); i++) {
                addEntry(other.getEntry(i));
            }
            iconsToRemove.addAll(other.iconsToRemove);
            iconsToAdd.putAll(other.iconsToAdd);
        }
    }

    public void removeSelectedEntry() {
        Entry selected = getSelected();
        if (selected == null) {
            return;
        }

        if (iconsToAdd.remove(selected.name) == null) {
            iconsToRemove.add(selected.name);
        }

        removeEntry(selected);
    }

    @Nullable
    public String addEntry(String name, Path file) {
        if (hasName(name)) {
            int suffix;
            for (suffix = 1; hasName(name + suffix); suffix++) {
                // noop
            }
            name = name + suffix;
        }

        return Entry.read(this, name, file).map(entry -> {
            iconsToAdd.put(entry.name, file);
            addEntry(entry);
            return null;
        }, Function.identity());
    }

    public void addInitialEntry(String name, IconRenderer iconRenderer) {
        addEntry(new Entry(name, iconRenderer));
    }

    private boolean hasName(String name) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getEntry(i).name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getIconsToRemove() {
        return iconsToRemove;
    }

    public Map<String, Path> getIconsToAdd() {
        return iconsToAdd;
    }

    @Override
    protected boolean isFocused() {
        return screen.getFocused() == this;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> implements AutoCloseable {
        private final String name;
        private final IconRenderer iconRenderer;
        private long lastClickTime;

        private Entry(String name, IconRenderer iconRenderer) {
            this.name = name;
            this.iconRenderer = iconRenderer;
        }

        private static Either<Entry, String> read(IconSelectionList list, String name, Path file) {
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                if (image.getWidth() != image.getHeight()
                    || image.getWidth() < Waypoint.MIN_ICON_DIMENSIONS
                    || image.getWidth() > Waypoint.MAX_ICON_DIMENSIONS
                    || !Mth.isPowerOfTwo(image.getWidth())
                ) {
                    image.close();
                    return Either.right("Image must be a square power of two between " + Waypoint.MIN_ICON_DIMENSIONS + "x" + Waypoint.MIN_ICON_DIMENSIONS + " and " + Waypoint.MAX_ICON_DIMENSIONS + "x" + Waypoint.MAX_ICON_DIMENSIONS + " pixels");
                }
                return Either.left(list.new Entry(name, file.toString(), image));
            } catch (IOException e) {
                LOGGER.warn("Unable to read image", e);
                return Either.right("Unable to read image: " + e);
            }
        }

        private Entry(String name, String filePath, NativeImage image) {
            this(name, IconRenderer.makeRendererFromImage(filePath, image));
        }

        public String getName() {
            return name;
        }

        @Override
        public void render(
            @NotNull PoseStack poseStack,
            int index,
            int top, int left,
            int width, int height,
            int mouseX, int mouseY,
            boolean isMouseOver,
            float partialTick
        ) {
            Font font = Minecraft.getInstance().font;
            font.draw(poseStack, name, left + 32 + 3, (float) (top + (height - font.lineHeight) / 2), 0xffffff);
            iconRenderer.render(poseStack, left, top, 32, 32, 1, 1, 1);
        }

        @Override
        public Component getNarration() {
            return Component.nullToEmpty(name);
        }

        @Override
        public void close() {
            iconRenderer.close();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            IconSelectionList.this.setSelected(this);
            long time = Util.getMillis();
            if (time - lastClickTime < 250) {
                confirmAction.run();
            } else {
                lastClickTime = time;
            }
            return true;
        }
    }

    @FunctionalInterface
    public interface IconRenderer extends AutoCloseable {
        IconRenderer NOOP = (poseStack, x, y, width, height, red, green, blue) -> {};

        void render(PoseStack poseStack, int x, int y, int width, int height, float red, float green, float blue);

        @Override
        default void close() {}

        static IconRenderer makeRendererFromImage(String filePath, NativeImage image) {
            DynamicTexture texture = new DynamicTexture(image);
            //noinspection UnstableApiUsage
            ResourceLocation textureLocation = new ResourceLocation(
                "minimapsync",
                "dynamic_icon_" + Util.sanitizeName(filePath, ResourceLocation::validPathChar) + "/" + Hashing.sha1().hashUnencodedChars(filePath)
            );
            Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
            return new TextureIconRenderer(textureLocation) {
                @Override
                public void close() {
                    texture.close();
                }
            };
        }
    }

    public static class TextureIconRenderer implements IconRenderer {
        private final ResourceLocation textureLocation;

        public TextureIconRenderer(ResourceLocation textureLocation) {
            this.textureLocation = textureLocation;
        }

        @Override
        public void render(PoseStack poseStack, int x, int y, int width, int height, float red, float green, float blue) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(red, green, blue, 1);
            RenderSystem.setShaderTexture(0, textureLocation);
            RenderSystem.enableBlend();
            GuiComponent.blit(poseStack, x, y, 0, 0, width, height, width, height);
            RenderSystem.disableBlend();
        }
    }
}
