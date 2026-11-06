package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Job;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** The layers that dress a citizen in their kingdom's colors. */
public final class CitizenLayers {
    private CitizenLayers() {}

    static Identifier tex(String name) {
        return DominionMod.id("textures/entity/citizen/" + name + ".png");
    }

    private static final Identifier TUNIC = tex("tunic");
    private static final Identifier TRIM = tex("tunic_trim");
    private static final Identifier TABARD = tex("tabard");
    private static final Identifier TABARD_TRIM = tex("tabard_trim");

    /** Work clothes (straw hat, apron, quiver...), the tunic in the primary color, and trim in the secondary. */
    public static class Clothes extends RenderLayer<CitizenRenderState, CitizenModel> {
        private final CitizenModel outfit, tunic, trim;

        public Clothes(RenderLayerParent<CitizenRenderState, CitizenModel> parent, CitizenModel outfit, CitizenModel tunic, CitizenModel trim) {
            super(parent);
            this.outfit = outfit;
            this.tunic = tunic;
            this.trim = trim;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (s.isInvisible) return;
            renderColoredCutoutModel(tunic, TUNIC, pose, collector, light, s, s.primary, 1);
            renderColoredCutoutModel(trim, TRIM, pose, collector, light, s, s.secondary, 2);
            if (s.job != Job.UNASSIGNED) {
                renderColoredCutoutModel(outfit, tex("job_" + s.job.name().toLowerCase()), pose, collector, light, s, 0xFFFFFFFF, 3);
            }
        }
    }

    /** Soldiers wear a tabard over their armor so every army is easy to tell apart. */
    public static class Tabard extends RenderLayer<CitizenRenderState, CitizenModel> {
        private final HumanoidModel<CitizenRenderState> model;

        public Tabard(RenderLayerParent<CitizenRenderState, CitizenModel> parent, HumanoidModel<CitizenRenderState> model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (s.isInvisible || !s.job.soldier) return;
            renderColoredCutoutModel(model, TABARD, pose, collector, light, s, s.primary, 4);
            renderColoredCutoutModel(model, TABARD_TRIM, pose, collector, light, s, s.secondary, 5);
        }
    }
}
