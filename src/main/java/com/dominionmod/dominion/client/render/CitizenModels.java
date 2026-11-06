package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.DominionMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CitizenModels {
    private CitizenModels() {}

    public static final ModelLayerLocation BODY = new ModelLayerLocation(DominionMod.id("citizen"), "main");
    public static final ModelLayerLocation TABARD = new ModelLayerLocation(DominionMod.id("citizen"), "tabard");

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BODY, CitizenModel::createLayer);
        e.registerLayerDefinition(TABARD, CitizenModel::createTabard);
    }
}
