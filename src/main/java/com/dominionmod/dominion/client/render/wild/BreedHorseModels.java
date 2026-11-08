package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.entity.wild.BreedHorse;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Layer definitions for the breed horses: body, foal, and saddle/armour shells that fit each body. */
final class BreedHorseModels {
    private BreedHorseModels() {}

    /** Vanilla horses are drawn at 1.1x; ours match so saddles and riders line up. */
    private static final MeshTransformer HORSE_SCALE = MeshTransformer.scaling(1.1f);

    static LayerDefinition body(BreedHorse.Breed breed) {
        MeshDefinition mesh = switch (breed) {
            case CLYDESDALE -> BeastMeshes.clydesdale();
            case MUSTANG -> BeastMeshes.mustang();
            case SHETLAND -> BeastMeshes.shetland();
        };
        return LayerDefinition.create(mesh, 128, 128).apply(HORSE_SCALE);
    }

    static LayerDefinition baby(BreedHorse.Breed breed) {
        return body(breed).apply(MeshTransformer.scaling(0.55f));
    }

    /** How much shorter the breed's legs are than a normal horse's (the pony's are 3 units shorter). */
    static float drop(BreedHorse.Breed breed) {
        return breed == BreedHorse.Breed.SHETLAND ? 3.0f : 0.0f;
    }

    static LayerDefinition armor(BreedHorse.Breed breed) {
        return LayerDefinition.create(equineBody(new CubeDeformation(0.1f), drop(breed)), 64, 64).apply(HORSE_SCALE);
    }

    /** Vanilla's saddle (and bridle) on a body lowered to the breed's leg length. */
    static LayerDefinition saddle(BreedHorse.Breed breed) {
        MeshDefinition mesh = equineBody(CubeDeformation.NONE, drop(breed));
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        PartDefinition headParts = root.getChild("head_parts");
        body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(26, 0).addBox(-5.0f, -8.0f, -9.0f, 10.0f, 9.0f, 9.0f, new CubeDeformation(0.5f)), PartPose.ZERO);
        headParts.addOrReplaceChild("left_saddle_mouth", CubeListBuilder.create().texOffs(29, 5).addBox(2.0f, -9.0f, -6.0f, 1.0f, 2.0f, 2.0f), PartPose.ZERO);
        headParts.addOrReplaceChild("right_saddle_mouth", CubeListBuilder.create().texOffs(29, 5).addBox(-3.0f, -9.0f, -6.0f, 1.0f, 2.0f, 2.0f), PartPose.ZERO);
        headParts.addOrReplaceChild("left_saddle_line", CubeListBuilder.create().texOffs(32, 2).addBox(3.1f, -6.0f, -8.0f, 0.0f, 3.0f, 16.0f),
                PartPose.rotation((float) (-Math.PI / 6), 0.0f, 0.0f));
        headParts.addOrReplaceChild("right_saddle_line", CubeListBuilder.create().texOffs(32, 2).addBox(-3.1f, -6.0f, -8.0f, 0.0f, 3.0f, 16.0f),
                PartPose.rotation((float) (-Math.PI / 6), 0.0f, 0.0f));
        headParts.addOrReplaceChild("head_saddle", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0f, -11.0f, -1.9f, 6.0f, 5.0f, 6.0f, new CubeDeformation(0.22f)), PartPose.ZERO);
        headParts.addOrReplaceChild("mouth_saddle_wrap", CubeListBuilder.create().texOffs(19, 0).addBox(-2.0f, -11.0f, -4.0f, 4.0f, 5.0f, 2.0f, new CubeDeformation(0.2f)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).apply(HORSE_SCALE);
    }

    /** Vanilla's equine body with everything above the legs lowered by {@code drop} and the legs shortened to match. */
    private static MeshDefinition equineBody(CubeDeformation g, float drop) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float leg = 11.0f - drop;
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5.0f, -8.0f, -17.0f, 10.0f, 10.0f, 22.0f, new CubeDeformation(0.05f)),
                PartPose.offset(0.0f, 11.0f + drop, 5.0f));
        PartDefinition headParts = root.addOrReplaceChild("head_parts",
                CubeListBuilder.create().texOffs(0, 35).addBox(-2.05f, -6.0f, -2.0f, 4.0f, 12.0f, 7.0f),
                PartPose.offsetAndRotation(0.0f, 4.0f + drop, -12.0f, (float) (Math.PI / 6), 0.0f, 0.0f));
        PartDefinition head = headParts.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 13).addBox(-3.0f, -11.0f, -2.0f, 6.0f, 5.0f, 7.0f, g), PartPose.ZERO);
        headParts.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(56, 36).addBox(-1.0f, -11.0f, 5.01f, 2.0f, 16.0f, 2.0f, g), PartPose.ZERO);
        headParts.addOrReplaceChild("upper_mouth", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0f, -11.0f, -7.0f, 4.0f, 5.0f, 5.0f, g), PartPose.ZERO);
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0f, -1.01f, -1.0f, 4.0f, leg, 4.0f, g),
                PartPose.offset(4.0f, 14.0f + drop, 7.0f));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(48, 21).addBox(-1.0f, -1.01f, -1.0f, 4.0f, leg, 4.0f, g),
                PartPose.offset(-4.0f, 14.0f + drop, 7.0f));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0f, -1.01f, -1.9f, 4.0f, leg, 4.0f, g),
                PartPose.offset(4.0f, 14.0f + drop, -10.0f));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(48, 21).addBox(-1.0f, -1.01f, -1.9f, 4.0f, leg, 4.0f, g),
                PartPose.offset(-4.0f, 14.0f + drop, -10.0f));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 36).addBox(-1.5f, 0.0f, 0.0f, 3.0f, 14.0f, 4.0f, g),
                PartPose.offsetAndRotation(0.0f, -5.0f, 2.0f, (float) (Math.PI / 6), 0.0f, 0.0f));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(19, 16).addBox(0.55f, -13.0f, 4.0f, 2.0f, 3.0f, 1.0f, new CubeDeformation(-0.001f)), PartPose.ZERO);
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(19, 16).addBox(-2.55f, -13.0f, 4.0f, 2.0f, 3.0f, 1.0f, new CubeDeformation(-0.001f)), PartPose.ZERO);
        return mesh;
    }
}
