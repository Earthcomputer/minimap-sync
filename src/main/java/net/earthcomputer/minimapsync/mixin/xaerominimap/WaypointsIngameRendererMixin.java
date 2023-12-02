package net.earthcomputer.minimapsync.mixin.xaerominimap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.earthcomputer.minimapsync.client.XaerosMapCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.render.WaypointsIngameRenderer;

@Mixin(WaypointsIngameRenderer.class)
public class WaypointsIngameRendererMixin {
    @ModifyExpressionValue(method = "drawIconInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I", remap = true))
    private int modifyFontWidthForCustomIcons(int original, PoseStack matrixStack, MinimapRendererHelper rendererHelper, Waypoint w) {
        if (w.getSymbol().startsWith("minimapsync_")) {
            return 7;
        } else {
            return original;
        }
    }

    @WrapOperation(
        method = "drawIconInWorld",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I", remap = true)),
        at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/render/WaypointsIngameRenderer;renderColorBackground(Lcom/mojang/blaze3d/vertex/PoseStack;IFFFFLcom/mojang/blaze3d/vertex/VertexConsumer;)V", ordinal = 0, remap = true)
    )
    private void drawCustomIcon(WaypointsIngameRenderer instance, PoseStack matrixStack, int addedFrame, float r, float g, float b, float a, VertexConsumer waypointBackgroundConsumer, Operation<Void> original, PoseStack matrixStack1, MinimapRendererHelper helper, Waypoint w) {
        if (w.getSymbol().startsWith("minimapsync_")) {
            XaerosMapCompat.drawCustomIcon(matrixStack, w.getSymbol(), -5 - addedFrame, -9, 9, 9, r, g, b, a);
        } else {
            original.call(instance, matrixStack, addedFrame, r, g, b, a, waypointBackgroundConsumer);
        }
    }

    @WrapOperation(method = "drawIconInWorld", at = @At(value = "INVOKE", target = "Lxaero/common/misc/Misc;drawNormalText(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFIZLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", remap = true))
    private void dontDrawSymbolStringForCustomIcons(PoseStack matrices, String symbol, float x, float y, int color, boolean shadow, MultiBufferSource.BufferSource renderTypeBuffer, Operation<Void> original) {
        if (!symbol.startsWith("minimapsync_")) {
            original.call(matrices, symbol, x, y, color, shadow, renderTypeBuffer);
        }
    }
}
