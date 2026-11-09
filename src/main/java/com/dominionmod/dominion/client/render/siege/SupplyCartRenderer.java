package com.dominionmod.dominion.client.render.siege;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.SupplyCart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Draws the supply cart hitched right behind its pony (worked out from the pony's own smooth position, so it never lags). */
public class SupplyCartRenderer extends EntityRenderer<SupplyCart, SupplyCartRenderState> {
    private static final Identifier TEXTURE = DominionMod.id("textures/entity/siege/supply_cart.png");
    private final SupplyCartModel model;

    public SupplyCartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new SupplyCartModel(ctx.bakeLayer(SiegeModels.CART));
        this.shadowRadius = 0.8f;
    }

    @Override
    public SupplyCartRenderState createRenderState() {
        return new SupplyCartRenderState();
    }

    @Override
    public void extractRenderState(SupplyCart cart, SupplyCartRenderState s, float partialTicks) {
        super.extractRenderState(cart, s, partialTicks);
        s.yRot = cart.getYRot(partialTicks);
        s.offX = s.offY = s.offZ = 0;
        Vec3 at = cart.getPosition(partialTicks);
        Entity pony = cart.level().getEntity(cart.ponyNetworkId());
        if (pony != null) {
            float yaw = pony.getYRot(partialTicks);
            Vec3 p = pony.getPosition(partialTicks);
            double r = Math.toRadians(yaw);
            Vec3 want = p.add(Math.sin(r) * SupplyCart.HITCH, 0, -Math.cos(r) * SupplyCart.HITCH);
            s.offX = want.x - at.x;
            s.offY = p.y - at.y;
            s.offZ = want.z - at.z;
            s.yRot = yaw;
            at = want;
        }
        s.wheel = (float) ((at.x + at.z) * 1.4);
    }

    @Override
    public void submit(SupplyCartRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(s.offX, s.offY, s.offZ);
        pose.rotate(Axis.YP.rotationDegrees(180.0f - s.yRot));
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        out.submitModel(this.model, s, pose, TEXTURE, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
