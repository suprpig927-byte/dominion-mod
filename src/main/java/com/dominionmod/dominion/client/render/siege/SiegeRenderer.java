package com.dominionmod.dominion.client.render.siege;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.siege.SiegeEngine;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** One renderer for every siege engine; the mesh and texture come from the engine kind. */
public class SiegeRenderer extends MobRenderer<SiegeEngine, SiegeRenderState, SiegeModel> {
    private final Identifier texture;
    private final float scale;

    public SiegeRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, String texture, float scale) {
        super(ctx, new SiegeModel(ctx.bakeLayer(layer)), 1.0f);
        this.texture = DominionMod.id("textures/entity/siege/" + texture + ".png");
        this.scale = scale;
    }

    @Override
    public SiegeRenderState createRenderState() {
        return new SiegeRenderState();
    }

    @Override
    public void extractRenderState(SiegeEngine e, SiegeRenderState s, float partialTicks) {
        super.extractRenderState(e, s, partialTicks);
        s.sinceAction = (float) (e.level().getGameTime() - e.lastAction()) + partialTicks;
        s.pitch = e.barrelPitch();
        s.wheel = (float) ((e.getX() + e.getZ()) * 1.2);
        s.scale *= scale;
    }

    @Override
    public Identifier getTextureLocation(SiegeRenderState s) {
        return texture;
    }
}
