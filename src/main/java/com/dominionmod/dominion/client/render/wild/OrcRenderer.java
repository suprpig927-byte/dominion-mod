package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.Orc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

public class OrcRenderer extends HumanoidMobRenderer<Orc, OrcRenderState, HumanoidModel<OrcRenderState>> {
    private static final Identifier ORC = DominionMod.id("textures/entity/orc.png");
    private static final Identifier CHIEF = DominionMod.id("textures/entity/orc_chieftain.png");

    public OrcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(WildModels.ORC)), 0.6f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, ctx.getModelSet(), part -> new HumanoidModel<OrcRenderState>(part)),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public OrcRenderState createRenderState() {
        return new OrcRenderState();
    }

    @Override
    public void extractRenderState(Orc orc, OrcRenderState s, float partialTicks) {
        super.extractRenderState(orc, s, partialTicks);
        s.chief = orc.isChief();
    }

    @Override
    public Identifier getTextureLocation(OrcRenderState s) {
        return s.chief ? CHIEF : ORC;
    }
}
