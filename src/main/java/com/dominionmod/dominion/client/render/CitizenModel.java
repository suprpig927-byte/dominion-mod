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
 * A citizen is a real villager, built on the vanilla villager's texture layout so it wears the
 * vanilla villager skin, biome clothes and profession outfits, but with free arms (sleeve + hand)
 * so it can hold tools and weapons and wear armor. Workers keep the villager robe; soldiers take it off.
 */
public class CitizenModel extends HumanoidModel<CitizenRenderState> {
    private final ModelPart robe;

    public CitizenModel(ModelPart root) {
        super(root);
        this.robe = root.getChild("body").getChild("jacket");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, -10.0f, -4.0f, 8.0f, 10.0f, 8.0f), PartPose.ZERO);
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0f, -10.0f, -4.0f, 8.0f, 10.0f, 8.0f, new CubeDeformation(0.51f)), PartPose.ZERO);
        hat.addOrReplaceChild("hat_rim", CubeListBuilder.create()
                .texOffs(30, 47).addBox(-8.0f, -8.0f, -6.0f, 16.0f, 16.0f, 1.0f), PartPose.rotation((float) (-Math.PI / 2), 0.0f, 0.0f));
        head.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-1.0f, -1.0f, -6.0f, 2.0f, 4.0f, 2.0f), PartPose.offset(0.0f, -2.0f, 0.0f));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 20).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f), PartPose.ZERO);
        body.addOrReplaceChild("jacket", CubeListBuilder.create()
                .texOffs(0, 38).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f, new CubeDeformation(0.5f)), PartPose.ZERO);
        // Sleeve from the villager's folded-arm texture, hand from the folded hands.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(44, 22).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f)
                .texOffs(40, 38).addBox(-3.0f, 6.0f, -2.0f, 4.0f, 4.0f, 4.0f), PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(44, 22).mirror().addBox(-1.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f)
                .texOffs(40, 38).mirror().addBox(-1.0f, 6.0f, -2.0f, 4.0f, 4.0f, 4.0f), PartPose.offset(5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f), PartPose.offset(-2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 22).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f), PartPose.offset(2.0f, 12.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(CitizenRenderState state) {
        super.setupAnim(state);
        robe.visible = !state.job.soldier;
        // The hat rim only shows when there's a profession hat to wear.
        this.hat.visible = true;
    }

    /**
     * The soldier's tabard: front and back panels over the armor, down past the belt. Textured
     * per kingdom at 4x resolution (256x128) with the kingdom's flag painted on.
     */
    public static LayerDefinition createTabard() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f, new CubeDeformation(1.05f))
                .texOffs(32, 0).addBox(-4.0f, 12.0f, -3.0f, 8.0f, 6.0f, 6.0f, new CubeDeformation(1.05f, 0.0f, 1.05f)), PartPose.ZERO);
        for (String part : new String[]{"head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            PartDefinition p = root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
            if (part.equals("head")) p.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
