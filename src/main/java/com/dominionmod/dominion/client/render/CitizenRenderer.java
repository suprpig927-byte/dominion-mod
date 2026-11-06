package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.entity.Citizen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DyeColor;

public class CitizenRenderer extends HumanoidMobRenderer<Citizen, CitizenRenderState, CitizenModel> {
    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CitizenModel(ctx.bakeLayer(CitizenModels.BODY)), 0.5f);
        this.addLayer(new CitizenLayers.Clothes(this, new CitizenModel(ctx.bakeLayer(CitizenModels.OUTFIT)),
                new CitizenModel(ctx.bakeLayer(CitizenModels.TUNIC)), new CitizenModel(ctx.bakeLayer(CitizenModels.TRIM))));
        this.addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.ZOMBIE_VILLAGER_ARMOR, ctx.getModelSet(), part -> new HumanoidModel<CitizenRenderState>(part)),
                ctx.getEquipmentRenderer()));
        this.addLayer(new CitizenLayers.Tabard(this, new HumanoidModel<>(ctx.bakeLayer(CitizenModels.TABARD)),
                new CitizenLayers.EmblemModel(ctx.bakeLayer(CitizenModels.EMBLEM)), ctx.getSprites()));
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(Citizen c, CitizenRenderState s, float partialTicks) {
        super.extractRenderState(c, s, partialTicks);
        s.primary = 0xFF000000 | DyeColor.byId(c.getPrimary()).getTextureDiffuseColor();
        s.secondary = 0xFF000000 | DyeColor.byId(c.getSecondary()).getTextureDiffuseColor();
        s.look = Math.floorMod(c.getLook(), 4);
        s.job = c.job();
        s.isAggressive = c.isAggressive();
        net.minecraft.world.item.ItemStack flag = c.getFlag();
        if (flag.getItem() instanceof net.minecraft.world.item.BannerItem banner) {
            s.flagBase = banner.getColor();
            s.flagPatterns = flag.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                    net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY);
        } else {
            s.flagBase = null;
            s.flagPatterns = net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY;
        }
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Citizen c, HumanoidArm arm) {
        if (c.getMainArm() == arm && c.isAggressive() && c.getMainHandItem().getItem() instanceof BowItem) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return super.getArmPose(c, arm);
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState s) {
        return CitizenLayers.tex("skin_" + s.look);
    }
}
