package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class WildModels {
    private WildModels() {}

    public static final ModelLayerLocation DEER = new ModelLayerLocation(DominionMod.id("deer"), "main");
    public static final ModelLayerLocation DEER_BABY = new ModelLayerLocation(DominionMod.id("deer"), "baby");
    public static final ModelLayerLocation CENTAUR = new ModelLayerLocation(DominionMod.id("centaur"), "main");
    public static final ModelLayerLocation ORC = new ModelLayerLocation(DominionMod.id("orc"), "main");

    private static final java.util.Map<String, ModelLayerLocation> HORSES = new java.util.HashMap<>();

    /** Layer for a breed horse: "main", "baby", "armor" or "saddle". */
    public static ModelLayerLocation horse(com.dominionmod.dominion.entity.wild.BreedHorse.Breed breed, String layer) {
        return HORSES.computeIfAbsent(breed.name() + "/" + layer,
                k -> new ModelLayerLocation(DominionMod.id("horse_" + breed.name().toLowerCase()), layer));
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (var breed : com.dominionmod.dominion.entity.wild.BreedHorse.Breed.values()) {
            e.registerLayerDefinition(horse(breed, "main"), () -> BreedHorseModels.body(breed));
            e.registerLayerDefinition(horse(breed, "baby"), () -> BreedHorseModels.baby(breed));
            e.registerLayerDefinition(horse(breed, "armor"), () -> BreedHorseModels.armor(breed));
            e.registerLayerDefinition(horse(breed, "saddle"), () -> BreedHorseModels.saddle(breed));
        }
        e.registerLayerDefinition(DEER, DeerModel::createBodyLayer);
        e.registerLayerDefinition(DEER_BABY, DeerModel::createBabyLayer);
        e.registerLayerDefinition(CENTAUR, CentaurModel::createBodyLayer);
        e.registerLayerDefinition(ORC, WildModels::orc);
    }

    /** A player-shaped body with tusks jutting up from the lower jaw and a heavier brow. */
    private static LayerDefinition orc() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        var head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("tusks", CubeListBuilder.create()
                .texOffs(56, 16).addBox(-3.0f, -3.0f, -5.0f, 1, 2, 1)
                .texOffs(56, 16).addBox(2.0f, -3.0f, -5.0f, 1, 2, 1)
                .texOffs(56, 20).addBox(-4.0f, -6.5f, -4.5f, 8, 1, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
