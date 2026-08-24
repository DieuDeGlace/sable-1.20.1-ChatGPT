package dev.ryanhcode.sable.render.dynamic_shade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Make all shade-less things have a normal pointing straight up for dynamic shading!
 */
public class SubLevelVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private boolean verticalNormal;

    public SubLevelVertexConsumer(final VertexConsumer delegate) {
        this.delegate = delegate;
    }


    @Override
    public VertexConsumer vertex(final double x, final double y, final double z) {
        this.delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(final int red, final int green, final int blue, final int alpha) {
        this.delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(final float u, final float v) {
        this.delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(final int u, final int v) {
        this.delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(final int u, final int v) {
        SableDynamicDirectionalShading.recordLightCoordinates(u, v);
        this.delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(final float pX, final float pY, final float pZ) {
        if (this.verticalNormal) {
            this.delegate.normal(0f, 1f, 0f);
        } else {
            this.delegate.normal(pX, pY, pZ);
        }
        return this;
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
    }

    @Override
    public void defaultColor(final int red, final int green, final int blue, final int alpha) {
        this.delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }

    @Override
    public void putBulkData(final PoseStack.Pose pose, final BakedQuad bakedQuad, final float[] colorMuls,
                            final float red, final float green, final float blue, final float alpha,
                            final int[] lights, final int overlay, final boolean mulColor) {
        this.verticalNormal = !bakedQuad.isShade();
        try {
            VertexConsumer.super.putBulkData(pose, bakedQuad, colorMuls, red, green, blue, alpha,
                    lights, overlay, mulColor);
        } finally {
            this.verticalNormal = false;
        }
    }

}
