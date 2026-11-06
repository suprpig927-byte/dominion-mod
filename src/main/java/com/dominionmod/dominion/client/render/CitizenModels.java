package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.DominionMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CitizenModels {
    private CitizenModels() {}

    public static final ModelLayerLocation BODY = new ModelLayerLocation(DominionMod.id("citizen"), "main");
    public static final ModelLayerLocation TUNIC = new ModelLayerLocation(DominionMod.id("citizen"), "tunic");
    public static final ModelLayerLocation TRIM = new ModelLayerLocation(DominionMod.id("citizen"), "trim");
    public static final ModelLayerLocation OUTFIT = new ModelLayerLocation(DominionMod.id("citizen"), "outfit");
    public static final ModelLayerLocation TABARD = new ModelLayerLocation(DominionMod.id("citizen"), "tabard");

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BODY, () -> CitizenModel.createLayer(CubeDeformation.NONE));
        e.registerLayerDefinition(TUNIC, () -> CitizenModel.createLayer(new CubeDeformation(0.04f)));
        e.registerLayerDefinition(TRIM, () -> CitizenModel.createLayer(new CubeDeformation(0.08f)));
        e.registerLayerDefinition(OUTFIT, () -> CitizenModel.createLayer(new CubeDeformation(0.12f)));
        e.registerLayerDefinition(TABARD, CitizenModel::createTabard);
    }
}
