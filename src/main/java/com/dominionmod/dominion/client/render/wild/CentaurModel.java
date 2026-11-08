package com.dominionmod.dominion.client.render.wild;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** A horse's body with a man's torso rising from the withers. The bow is part of the model, held in the left hand. */
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

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, -5.0f, -9.0f, 10, 10, 20),
                PartPose.offset(0.0f, 11.0f, 3.0f));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(96, 16).addBox(-1.0f, 0.0f, -1.0f, 2, 9, 2),
                PartPose.offsetAndRotation(0.0f, -4.0f, 11.0f, 0.55f, 0.0f, 0.0f));
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(96, 0).addBox(-4.0f, -12.0f, -2.0f, 8, 12, 4),
                PartPose.offset(0.0f, 8.0f, -5.0f));
        torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                PartPose.offset(0.0f, -12.0f, 0.0f));
        torso.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(64, 16).addBox(-3.0f, -2.0f, -2.0f, 4, 12, 4),
                PartPose.offset(-5.0f, -10.0f, 0.0f));
        PartDefinition left = torso.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(80, 16).addBox(-1.0f, -2.0f, -2.0f, 4, 12, 4),
                PartPose.offset(5.0f, -10.0f, 0.0f));
        left.addOrReplaceChild("bow", CubeListBuilder.create()
                        .texOffs(112, 16).addBox(-0.5f, -6.0f, -0.5f, 1, 12, 1)
                        .texOffs(118, 16).addBox(-0.5f, -5.5f, 1.5f, 1, 11, 1),
                PartPose.offset(1.0f, 9.0f, -2.5f));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5f, 0.0f, -1.5f, 3, 8, 3),
                PartPose.offset(-3.0f, 16.0f, 11.0f));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(16, 32).addBox(-1.5f, 0.0f, -1.5f, 3, 8, 3),
                PartPose.offset(3.0f, 16.0f, 11.0f));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(32, 32).addBox(-1.5f, 0.0f, -1.5f, 3, 8, 3),
                PartPose.offset(-3.0f, 16.0f, -4.0f));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(48, 32).addBox(-1.5f, 0.0f, -1.5f, 3, 8, 3),
                PartPose.offset(3.0f, 16.0f, -4.0f));
        return LayerDefinition.create(mesh, 128, 64);
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
