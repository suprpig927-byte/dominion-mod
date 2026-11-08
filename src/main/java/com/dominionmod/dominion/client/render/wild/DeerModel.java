package com.dominionmod.dominion.client.render.wild;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.util.Mth;

/**
 * A white-tailed deer: deep chest and tucked belly, a neck that sweeps up and forward, a fine head
 * with big ears, slim jointed legs, a flag tail, and on stags branching antlers with brow tines.
 * The mesh comes from tools/gen_beasts.py (BeastMeshes), the same script that paints the coat.
 */
public class DeerModel extends QuadrupedModel<DeerRenderState> {
    private final ModelPart antlers;
    private final ModelPart tail;
    private final ModelPart leftEar;
    private final ModelPart rightEar;

    public DeerModel(ModelPart root) {
        super(root);
        ModelPart skull = this.head.getChild("neck").getChild("skull");
        this.antlers = skull.getChild("antlers");
        this.leftEar = skull.getChild("left_ear");
        this.rightEar = skull.getChild("right_ear");
        this.tail = this.body.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(BeastMeshes.deer(), 128, 64);
    }

    public static LayerDefinition createBabyLayer() {
        return createBodyLayer().apply(MeshTransformer.scaling(0.55f));
    }

    @Override
    public void setupAnim(DeerRenderState state) {
        super.setupAnim(state);
        this.antlers.visible = state.stag && !state.isBaby;
        // Ears flick now and then; the tail flags up when running.
        float t = state.ageInTicks;
        float flick = Mth.sin(t * 0.9f) * Mth.clamp(Mth.sin(t * 0.07f) * 6.0f - 5.0f, 0.0f, 1.0f) * 0.35f;
        this.leftEar.zRot += flick;
        this.rightEar.zRot -= flick * 0.6f;
        this.tail.xRot += Mth.clamp(state.walkAnimationSpeed * 1.6f, 0.0f, 1.3f);
    }
}
