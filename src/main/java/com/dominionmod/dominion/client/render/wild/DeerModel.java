package com.dominionmod.dominion.client.render.wild;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** A slender deer: long legs, a raised neck and head, big ears, a short tail and (on stags) branching antlers. */
public class DeerModel extends QuadrupedModel<DeerRenderState> {
    private final ModelPart antlers;

    public DeerModel(ModelPart root) {
        super(root);
        this.antlers = this.head.getChild("antlers");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(48, 0).addBox(-1.5f, -6.0f, -2.0f, 3, 7, 3)          // neck
                        .texOffs(0, 24).addBox(-2.5f, -9.5f, -4.5f, 5, 4, 6)          // skull
                        .texOffs(24, 24).addBox(-1.5f, -8.5f, -7.5f, 3, 3, 3)         // muzzle
                        .texOffs(40, 24).addBox(-4.5f, -10.0f, -1.5f, 2, 1, 1)        // ears
                        .texOffs(40, 27).addBox(2.5f, -10.0f, -1.5f, 2, 1, 1),
                PartPose.offset(0.0f, 6.0f, -7.0f));
        head.addOrReplaceChild("antlers", CubeListBuilder.create()
                        .texOffs(48, 16).addBox(-2.5f, -15.5f, -1.0f, 1, 6, 1)
                        .texOffs(48, 16).addBox(1.5f, -15.5f, -1.0f, 1, 6, 1)
                        .texOffs(48, 16).addBox(-4.5f, -13.5f, -1.0f, 2, 1, 1)
                        .texOffs(48, 16).addBox(2.5f, -13.5f, -1.0f, 2, 1, 1)
                        .texOffs(48, 16).addBox(-4.5f, -16.5f, -1.0f, 1, 3, 1)
                        .texOffs(48, 16).addBox(3.5f, -16.5f, -1.0f, 1, 3, 1)
                        .texOffs(48, 16).addBox(-2.5f, -17.5f, 0.0f, 1, 2, 1)
                        .texOffs(48, 16).addBox(1.5f, -17.5f, 0.0f, 1, 2, 1),
                PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -8.0f, 8, 8, 16)
                        .texOffs(32, 34).addBox(-1.0f, -4.0f, 8.0f, 2, 3, 2),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 34).addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(-2.5f, 12.0f, 6.0f));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(8, 34).addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(2.5f, 12.0f, 6.0f));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(16, 34).addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(-2.5f, 12.0f, -6.0f));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(24, 34).addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(2.5f, 12.0f, -6.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createBabyLayer() {
        return createBodyLayer().apply(MeshTransformer.scaling(0.55f));
    }

    @Override
    public void setupAnim(DeerRenderState state) {
        super.setupAnim(state);
        this.antlers.visible = state.stag && !state.isBaby;
        this.head.xRot += 0.15f;
    }
}
