package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.IllagerOverlord;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.resources.Identifier;

/** The Overlord: an evoker in imperial purple and gold, with a crown. */
public class OverlordRenderer extends EvokerRenderer<IllagerOverlord> {
    private static final Identifier TEXTURE = DominionMod.id("textures/entity/illager/overlord.png");

    public OverlordRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(EvokerRenderState state) {
        return TEXTURE;
    }
}
