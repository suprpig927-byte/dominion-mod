package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.Otter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class OtterRenderer extends AgeableMobRenderer<Otter, OtterRenderState, OtterModel> {
    private static final Identifier[] COATS = {DominionMod.id("textures/entity/otter.png"), DominionMod.id("textures/entity/otter_sea.png")};

    public OtterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new OtterModel(ctx.bakeLayer(WildModels.OTTER)), new OtterModel(ctx.bakeLayer(WildModels.OTTER_BABY)), 0.4f);
    }

    @Override
    public OtterRenderState createRenderState() {
        return new OtterRenderState();
    }

    @Override
    public void extractRenderState(Otter otter, OtterRenderState s, float partialTicks) {
        super.extractRenderState(otter, s, partialTicks);
        s.variant = otter.variant();
        s.floating = otter.isFloating();
        s.sitting = otter.isInSittingPose();
    }

    @Override
    protected void setupRotations(OtterRenderState s, PoseStack pose, float bodyRot, float scale) {
        super.setupRotations(s, pose, bodyRot, scale);
        if (s.floating) {
            // roll onto its back, belly to the sky, floating at the surface
            pose.translate(0.0f, 0.42f * scale, 0.0f);
            pose.rotateAround(Axis.ZP.rotationDegrees(180.0f), 0.0f, 0.12f * scale, 0.0f);
            pose.translate(0.0f, Math.sin(s.ageInTicks * 0.1f) * 0.02f, 0.0f);
        }
    }

    @Override
    public Identifier getTextureLocation(OtterRenderState s) {
        return COATS[Math.floorMod(s.variant, COATS.length)];
    }
}
