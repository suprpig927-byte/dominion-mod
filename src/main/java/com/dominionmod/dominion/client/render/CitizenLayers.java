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

    /**
     * Soldiers wear a tabard over their armor so every army is easy to tell apart, with the
     * kingdom's own flag (every pattern layer) on the chest, drawn like a shield's design.
     */
    public static class Tabard extends RenderLayer<CitizenRenderState, CitizenModel> {
        private final HumanoidModel<CitizenRenderState> model;
        private final EmblemModel emblem;
        private final net.minecraft.client.resources.model.sprite.SpriteGetter sprites;

        public Tabard(RenderLayerParent<CitizenRenderState, CitizenModel> parent, HumanoidModel<CitizenRenderState> model, EmblemModel emblem,
                      net.minecraft.client.resources.model.sprite.SpriteGetter sprites) {
            super(parent);
            this.model = model;
            this.emblem = emblem;
            this.sprites = sprites;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (s.isInvisible || !s.job.soldier) return;
            renderColoredCutoutModel(model, TABARD, pose, collector, light, s, s.primary, 4);
            renderColoredCutoutModel(model, TABARD_TRIM, pose, collector, light, s, s.secondary, 5);
            if (s.flagBase == null) return;
            // The emblem rides on the chest: follow the body, then sit just in front of the tabard.
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(0.0f, 5.6f / 16.0f, -3.4f / 16.0f);
            pose.scale(0.42f, 0.42f, 0.42f);
            net.minecraft.client.renderer.blockentity.BannerRenderer.submitPatterns(sprites, pose, collector, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, emblem, net.minecraft.util.Unit.INSTANCE, false,
                    s.flagBase, s.flagPatterns);
            pose.popPose();
        }
    }

    /** A flat plate the shape of a shield's face, which the flag's patterns are painted onto. */
    public static class EmblemModel extends net.minecraft.client.model.Model<net.minecraft.util.Unit> {
        public EmblemModel(net.minecraft.client.model.geom.ModelPart root) {
            super(root, net.minecraft.client.renderer.rendertype.RenderTypes::entityCutout);
        }

        public static net.minecraft.client.model.geom.builders.LayerDefinition createLayer() {
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            mesh.getRoot().addOrReplaceChild("plate", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-6.0f, -11.0f, -0.5f, 12.0f, 22.0f, 1.0f), net.minecraft.client.model.geom.PartPose.ZERO);
            return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64);
        }
    }
}
