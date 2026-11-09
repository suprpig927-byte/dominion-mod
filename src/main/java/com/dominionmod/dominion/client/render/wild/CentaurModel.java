package com.dominionmod.dominion.client.render.wild;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/** A horse's body with a man's torso rising from the withers: long hair and a beard, leather straps, a quiver, and a recurve bow in the left hand. */
public class CentaurModel extends EntityModel<CentaurRenderState> {
    private final ModelPart torso, head, rightArm, leftArm, tail;
    private final ModelPart rightHind, leftHind, rightFront, leftFront;

    public CentaurModel(ModelPart root) {
        super(root);
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.rightArm = torso.getChild("right_arm");
        this.leftArm = torso.getChild("left_arm");
        this.tail = root.getChild("body").getChild("tail");
        this.rightHind = root.getChild("right_hind_leg");
        this.leftHind = root.getChild("left_hind_leg");
        this.rightFront = root.getChild("right_front_leg");
        this.leftFront = root.getChild("left_front_leg");
    }

    /** The mesh comes from tools/gen_beasts.py (BeastMeshes), which paints the matching textures. */
    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(BeastMeshes.centaur(), 128, 128);
    }

    @Override
    public void setupAnim(CentaurRenderState s) {
        super.setupAnim(s);
        float pos = s.walkAnimationPos, speed = s.walkAnimationSpeed;
        this.rightHind.xRot = Mth.cos(pos * 0.6662f) * 1.2f * speed;
        this.leftHind.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.2f * speed;
        this.rightFront.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.2f * speed;
        this.leftFront.xRot = Mth.cos(pos * 0.6662f) * 1.2f * speed;
        this.tail.xRot = 0.55f + Mth.cos(s.ageInTicks * 0.1f) * 0.08f + speed * 0.6f;
        this.head.yRot = s.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = s.xRot * Mth.DEG_TO_RAD;
        this.torso.xRot = -0.08f;
        if (s.aiming) {
            this.leftArm.xRot = -Mth.HALF_PI;
            this.leftArm.yRot = 0.1f + this.head.yRot;
            this.rightArm.xRot = -Mth.HALF_PI;
            this.rightArm.yRot = -0.5f + this.head.yRot;
        } else {
            this.rightArm.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 0.6f * speed + Mth.sin(s.ageInTicks * 0.067f) * 0.05f;
            this.leftArm.xRot = -0.35f + Mth.cos(pos * 0.6662f) * 0.4f * speed;
            this.rightArm.zRot = 0.06f;
            this.leftArm.zRot = -0.06f;
        }
    }
}
