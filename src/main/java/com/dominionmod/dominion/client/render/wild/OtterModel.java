package com.dominionmod.dominion.client.render.wild;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.util.Mth;

/**
 * The otter: a long low body, a broad flat head with a whiskered muzzle, little round ears, short
 * webbed legs and a thick tapering tail. On land it lopes; in the water the whole body undulates and
 * the tail sculls; sitting, it rears up on its haunches. (Floating on its back is done by the renderer.)
 */
public class OtterModel extends EntityModel<OtterRenderState> {
    private final ModelPart body, tail, tailTip, head, rightFront, leftFront, rightHind, leftHind;

    public OtterModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.tail = body.getChild("tail");
        this.tailTip = tail.getChild("tail_tip");
        this.head = root.getChild("head");
        this.rightFront = root.getChild("right_front_leg");
        this.leftFront = root.getChild("left_front_leg");
        this.rightHind = root.getChild("right_hind_leg");
        this.leftHind = root.getChild("left_hind_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(PetMeshes.otter(), 64, 64);
    }

    public static LayerDefinition createBabyLayer() {
        return createBodyLayer().apply(MeshTransformer.scaling(0.55f));
    }

    @Override
    public void setupAnim(OtterRenderState s) {
        super.setupAnim(s);
        float t = s.ageInTicks, pos = s.walkAnimationPos, spd = Math.min(1.0f, s.walkAnimationSpeed);
        head.yRot = s.yRot * Mth.DEG_TO_RAD;
        head.xRot = s.xRot * Mth.DEG_TO_RAD;
        if (s.sitting) {
            body.xRot = -0.55f;
            body.y += 1.0f;
            head.y -= 2.4f;
            head.z += 1.2f;
            head.xRot += 0.45f;
            rightFront.xRot = leftFront.xRot = -0.3f;
            rightFront.y -= 1.6f;
            leftFront.y -= 1.6f;
            rightFront.z += 1.0f;
            leftFront.z += 1.0f;
            rightHind.xRot = leftHind.xRot = -1.3f;
            tail.xRot = 0.55f;
            tailTip.xRot = -0.2f;
            return;
        }
        if (s.isInWater && !s.floating) {
            // swimming: the body ripples and the tail sculls; legs tuck back and paddle
            float w = t * 0.35f;
            body.xRot = Mth.sin(w) * 0.12f;
            head.xRot += Mth.sin(w - 0.6f) * 0.15f;
            tail.xRot = -0.15f + Mth.sin(w + 1.0f) * 0.45f;
            tailTip.xRot = Mth.sin(w + 2.0f) * 0.55f;
            rightFront.xRot = 0.9f + Mth.sin(w * 2) * 0.6f;
            leftFront.xRot = 0.9f - Mth.sin(w * 2) * 0.6f;
            rightHind.xRot = leftHind.xRot = 1.3f;
            return;
        }
        if (s.floating) {
            // on its back: paws folded on the chest, tail drifting
            rightFront.xRot = leftFront.xRot = -0.6f + Mth.sin(t * 0.08f) * 0.1f;
            rightHind.xRot = leftHind.xRot = 0.4f;
            tail.xRot = Mth.sin(t * 0.05f) * 0.2f;
            return;
        }
        // loping gait: legs in pairs, a little arch of the back
        float g = pos * 0.9f;
        rightFront.xRot = Mth.cos(g) * 1.1f * spd;
        leftFront.xRot = Mth.cos(g + 0.4f) * 1.1f * spd;
        rightHind.xRot = Mth.cos(g + Mth.PI) * 1.1f * spd;
        leftHind.xRot = Mth.cos(g + Mth.PI + 0.4f) * 1.1f * spd;
        body.xRot = Mth.sin(g) * 0.08f * spd;
        tail.xRot = -0.15f + Mth.cos(g) * 0.15f * spd + Mth.sin(t * 0.07f) * 0.05f;
        tail.yRot = Mth.sin(t * 0.1f) * 0.15f * (1 - spd);
        tailTip.xRot = 0.1f + Mth.cos(g + 1) * 0.2f * spd;
    }
}
