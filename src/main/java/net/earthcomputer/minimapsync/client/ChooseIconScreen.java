package net.earthcomputer.minimapsync.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.earthcomputer.minimapsync.MinimapSync;
import net.earthcomputer.minimapsync.model.Model;
import net.earthcomputer.minimapsync.model.Waypoint;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** @noinspection ConstantConditions */ // @Nullable on this.minecraft is annoying
public class ChooseIconScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Component TITLE = Component.nullToEmpty("Choose Icon");

    private final Screen parent;
    @Nullable
    private final String initialSelection;
    private final Consumer<@Nullable String> resultConsumer;
    private final Function<String, IconSelectionList.@Nullable IconRenderer> iconRendererSupplier;
    private IconSelectionList selectionList;
    private Button doneButton;
    private Button deleteButton;

    public ChooseIconScreen(
        Screen parent,
        @Nullable String initialSelection,
        Consumer<@Nullable String> resultConsumer,
        Function<String, IconSelectionList.@Nullable IconRenderer> iconRendererSupplier
    ) {
        super(TITLE);
        this.parent = parent;
        this.initialSelection = initialSelection;
        this.resultConsumer = resultConsumer;
        this.iconRendererSupplier = iconRendererSupplier;
    }

    @Override
    protected void init() {
        boolean firstInit = selectionList == null;
        selectionList = new IconSelectionList(
            minecraft,
            this::onDone,
            this,
            width, height,
            48, height - 64,
            36,
            selectionList
        ) {
            @Override
            public void setSelected(@Nullable IconSelectionList.Entry selected) {
                super.setSelected(selected);
                doneButton.active = selected != null;
                deleteButton.active = selected != null;
            }
        };
        if (firstInit) {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                Model model = Model.get(connection);
                for (String icon : (Iterable<String>) model.icons().names().stream().sorted(String.CASE_INSENSITIVE_ORDER)::iterator) {
                    selectionList.addInitialEntry(icon, Objects.requireNonNullElse(iconRendererSupplier.apply(icon), IconSelectionList.IconRenderer.NOOP));
                }
            }
        }
        addWidget(selectionList);
        doneButton = addRenderableWidget(new Button(
            width / 2 - 154, height - 52, 150, 20,
            MinimapSync.createComponent("{\"translate\": \"gui.done\"}"), button -> onDone()));
        addRenderableWidget(new Button(
            width / 2 + 4, height - 52, 150, 20,
            MinimapSync.createComponent("{\"translate\": \"gui.cancel\"}"), button -> onClose()));
        addRenderableWidget(new Button(
            width / 2 - 154, height - 28, 150, 20,
            Component.nullToEmpty("Add New Icon"), button -> onAdd()));
        deleteButton = addRenderableWidget(new Button(
            width / 2 + 4, height - 28, 150, 20,
            Component.nullToEmpty("Delete"), button -> selectionList.removeSelectedEntry()));
        setInitialFocus(selectionList);
        if (firstInit) {
            for (IconSelectionList.Entry child : selectionList.children()) {
                if (child.getName().equals(initialSelection)) {
                    selectionList.setSelected(child);
                    break;
                }
            }
        }
    }

    @Override
    public void render(@NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        selectionList.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, font, title, width / 2, 8, 0xffffff);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void onDone() {
        for (String toRemove : selectionList.getIconsToRemove()) {
            MinimapSyncClient.onRemoveIcon(null, toRemove);
        }
        selectionList.getIconsToAdd().forEach((name, path) -> {
            // Make sure the image hasn't been swapped out from beneath us, and convert to PNG while we're at it
            try (InputStream in = Files.newInputStream(path);
                 NativeImage image = NativeImage.read(in)
            ) {
                if (image.getWidth() != image.getHeight()
                    || image.getWidth() < Waypoint.MIN_ICON_DIMENSIONS
                    || image.getWidth() > Waypoint.MAX_ICON_DIMENSIONS
                    || !Mth.isPowerOfTwo(image.getWidth())
                ) {
                    return;
                }
                MinimapSyncClient.onAddIcon(null, name, image.asByteArray()); // asByteArray() is PNG format
            } catch (IOException e) {
                LOGGER.error("Failed to read icon", e);
            }
        });

        IconSelectionList.Entry selected = selectionList.getSelected();
        if (selected != null) {
            resultConsumer.accept(selected.getName());
        }

        onClose();
    }

    private void onAdd() {
        String fileChooseResult;
        String[] chosenFiles;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer extensions = stack.pointers(
                stack.UTF8("*.png"),
                stack.UTF8("*.jpg"),
                stack.UTF8("*.jpeg"),
                stack.UTF8("*.bmp")
            );
            fileChooseResult = TinyFileDialogs.tinyfd_openFileDialog("Choose Icons", null, extensions, "Image files", true);
        }
        if (fileChooseResult == null) {
            return;
        }
        chosenFiles = fileChooseResult.split("\\|");

        Set<String> errors = new LinkedHashSet<>();
        for (String file : chosenFiles) {
            // Normalize file separators
            if (!"/".equals(File.separator)) {
                file = file.replace("/", File.separator);
            }

            String iconName = file.substring(file.lastIndexOf(File.separator) + File.separator.length());
            int dotIndex = iconName.lastIndexOf('.');
            if (dotIndex > 0) {
                iconName = iconName.substring(0, dotIndex);
            }
            if (iconName.isBlank()) {
                iconName = "icon";
            }

            String error = selectionList.addEntry(iconName, Path.of(file));
            if (error != null) {
                errors.add(error);
            }
        }

        if (!errors.isEmpty()) {
            minecraft.setScreen(new ConfirmScreen(
                result -> minecraft.setScreen(this),
                Component.nullToEmpty("Some errors occurred importing icon(s)"),
                Component.nullToEmpty(String.join("\n", errors))
            ) {
                @Override
                protected void addButtons(int y) {
                    addExitButton(new Button(
                        width / 2 - 75, y, 150, 20,
                        MinimapSync.createComponent("{\"translate\": \"gui.ok\"}"), button -> callback.accept(false)));
                }
            });
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        for (IconSelectionList.Entry entry : selectionList.children()) {
            entry.close();
        }
        super.removed();
    }
}
