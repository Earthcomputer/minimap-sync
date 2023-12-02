package net.earthcomputer.minimapsync.mixin.xaerominimap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.earthcomputer.minimapsync.client.XaerosMapCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.render.WaypointsGuiRenderer;

@Mixin(value = WaypointsGuiRenderer.class, remap = false)
public class WaypointsGuiRendererMixin {
    @ModifyExpressionValue(method = "drawIconOnGUI", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I", remap = true))
    private int modifyFontWidthForCustomIcons(int original, PoseStack matrixStack, MinimapRendererHelper rendererHelper, Waypoint w) {
        if (w.getSymbol().startsWith("minimapsync_")) {
            return 7;
        } else {
            return original;
        }
    }

    @WrapOperation(method = "drawIconOnGUI", at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;addColoredRectToExistingBuffer(Lcom/mojang/math/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIIFFFF)V", remap = true))
    private void drawCustomIcon(MinimapRendererHelper instance, Matrix4f matrix, VertexConsumer vertexBuffer, float x, float y, int w, int h, float r, float g, float b, float a, Operation<Void> original, PoseStack matrixStack, MinimapRendererHelper rendererHelper, Waypoint waypoint) {
        if (waypoint.getSymbol().startsWith("minimapsync_")) {
            XaerosMapCompat.drawCustomIcon(matrixStack, waypoint.getSymbol(), x, y, w, h, r, g, b, a);
        } else {
            original.call(instance, matrix, vertexBuffer, x, y, w, h, r, g, b, a);
        }
    }

    @WrapOperation(method = "drawIconOnGUI", at = @At(value = "INVOKE", target = "Lxaero/common/misc/Misc;drawNormalText(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFIZLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", remap = true))
    private void dontDrawSymbolStringForCustomIcons(PoseStack matrices, String symbol, float x, float y, int color, boolean shadow, MultiBufferSource.BufferSource renderTypeBuffer, Operation<Void> original) {
        if (!symbol.startsWith("minimapsync_")) {
            original.call(matrices, symbol, x, y, color, shadow, renderTypeBuffer);
        }
    }
}
