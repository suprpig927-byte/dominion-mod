package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.Deer;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class DeerRenderer extends AgeableMobRenderer<Deer, DeerRenderState, DeerModel> {
    private static final Identifier TEXTURE = DominionMod.id("textures/entity/deer.png");

    public DeerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DeerModel(ctx.bakeLayer(WildModels.DEER)), new DeerModel(ctx.bakeLayer(WildModels.DEER_BABY)), 0.6f);
    }

    @Override
    public DeerRenderState createRenderState() {
        return new DeerRenderState();
    }

    @Override
    public void extractRenderState(Deer deer, DeerRenderState s, float partialTicks) {
        super.extractRenderState(deer, s, partialTicks);
        s.stag = deer.isStag();
    }

    @Override
    public Identifier getTextureLocation(DeerRenderState s) {
        return TEXTURE;
    }
}
