package com.dominionmod.dominion.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A citizen: a villager's long head and nose on a body with real arms and legs, so they can hold
 * tools and weapons and wear armor. 64x64 texture; same layout as a zombie villager minus the robe.
 */
public class CitizenModel extends HumanoidModel<CitizenRenderState> {
    public CitizenModel(ModelPart root) {
        super(root);
    }

    /** The body, and (with a small {@code g}) the clothing layers drawn just over it. */
    public static LayerDefinition createLayer(CubeDeformation g) {
        MeshDefinition mesh = HumanoidModel.createMesh(g, 0.0f);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -10.0f, -4.0f, 8.0f, 10.0f, 8.0f, g)
                        .texOffs(24, 0).addBox(-1.0f, -3.0f, -6.0f, 2.0f, 4.0f, 2.0f, g),
                PartPose.ZERO);
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0f, -10.0f, -4.0f, 8.0f, 10.0f, 8.0f, g.extend(0.5f)), PartPose.ZERO);
        hat.addOrReplaceChild("hat_rim", CubeListBuilder.create()
                .texOffs(30, 47).addBox(-8.0f, -8.0f, -6.0f, 16.0f, 16.0f, 1.0f, g), PartPose.rotation((float) (-Math.PI / 2), 0.0f, 0.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 20).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f, g), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(44, 22).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, g), PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(44, 22).mirror().addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, g), PartPose.offset(5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, g), PartPose.offset(-2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 22).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, g), PartPose.offset(2.0f, 12.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * The soldier's tabard: front and back panels hanging over the armor, down past the belt.
     * 64x32 texture: the chest at (0,0) and the skirt at (32,0), each laid out like a normal box.
     */
    public static LayerDefinition createTabard() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        PartDefinition root = mesh.getRoot();
        CubeDeformation g = new CubeDeformation(1.25f);
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, g)
                .texOffs(32, 0).addBox(-4.0f, 12.5f, -2.0f, 8.0f, 6.0f, 4.0f, new CubeDeformation(1.25f, 0.0f, 1.25f)), PartPose.ZERO);
        // Everything else is empty so only the tabard draws.
        for (String part : new String[]{"head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            PartDefinition p = root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
            if (part.equals("head")) p.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
