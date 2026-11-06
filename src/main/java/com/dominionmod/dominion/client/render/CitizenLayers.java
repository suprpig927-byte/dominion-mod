package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Job;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** The layers that dress a citizen: biome clothes, a work outfit, a sash in kingdom colors, and the soldier's tabard. */
public final class CitizenLayers {
    private CitizenLayers() {}

    static Identifier tex(String name) {
        return DominionMod.id("textures/entity/citizen/" + name + ".png");
    }

    static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace("textures/entity/villager/" + path + ".png");
    }

    private static final Identifier SASH = tex("sash");
    private static final Identifier BELT = tex("belt");

    /** Vanilla profession outfits that suit each job. Soldiers wear a tabard instead. */
    static String profession(Job job) {
        return switch (job) {
            case FARMER -> "farmer";
            case LUMBERJACK -> "toolsmith";
            case BUILDER -> "mason";
            default -> null;
        };
    }

    public static class Clothes extends RenderLayer<CitizenRenderState, CitizenModel> {
        public Clothes(RenderLayerParent<CitizenRenderState, CitizenModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (s.isInvisible) return;
            CitizenModel m = getParentModel();
            renderColoredCutoutModel(m, vanilla("type/" + s.villagerType), pose, collector, light, s, 0xFFFFFFFF, 1);
            String prof = profession(s.job);
            if (prof != null) renderColoredCutoutModel(m, vanilla("profession/" + prof), pose, collector, light, s, 0xFFFFFFFF, 2);
            renderColoredCutoutModel(m, SASH, pose, collector, light, s, s.primary, 3);
            renderColoredCutoutModel(m, BELT, pose, collector, light, s, s.secondary, 4);
        }
    }

    /** Soldiers wear a tabard over their armor with the kingdom's flag painted across it. */
    public static class Tabard extends RenderLayer<CitizenRenderState, CitizenModel> {
        private final HumanoidModel<CitizenRenderState> model;

        public Tabard(RenderLayerParent<CitizenRenderState, CitizenModel> parent, HumanoidModel<CitizenRenderState> model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (s.isInvisible || !s.job.soldier) return;
            Identifier texture = FlagTextures.tabard(s.flagBase, s.flagPatterns, s.primaryDye, s.secondaryDye);
            renderColoredCutoutModel(model, texture, pose, collector, light, s, 0xFFFFFFFF, 6);
        }
    }
}
