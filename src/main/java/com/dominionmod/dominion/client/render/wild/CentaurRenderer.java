package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.Centaur;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class CentaurRenderer extends MobRenderer<Centaur, CentaurRenderState, CentaurModel> {
    private static final Identifier[] LOOKS = {DominionMod.id("textures/entity/centaur_bay.png"),
            DominionMod.id("textures/entity/centaur_chestnut.png"), DominionMod.id("textures/entity/centaur_grey.png")};

    public CentaurRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CentaurModel(ctx.bakeLayer(WildModels.CENTAUR)), 0.9f);
    }

    @Override
    public CentaurRenderState createRenderState() {
        return new CentaurRenderState();
    }

    @Override
    public void extractRenderState(Centaur c, CentaurRenderState s, float partialTicks) {
        super.extractRenderState(c, s, partialTicks);
        s.aiming = c.isAggressive();
        s.look = (int) Math.floorMod(c.getUUID().getLeastSignificantBits(), (long) LOOKS.length);
    }

    @Override
    public Identifier getTextureLocation(CentaurRenderState s) {
        return LOOKS[s.look];
    }
}
