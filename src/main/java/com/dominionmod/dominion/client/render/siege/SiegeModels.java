package com.dominionmod.dominion.client.render.siege;

import com.dominionmod.dominion.DominionMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layers for the supply cart and the siege engines (meshes from tools/gen_engines.py). */
public final class SiegeModels {
    private SiegeModels() {}

    public static final ModelLayerLocation CART = new ModelLayerLocation(DominionMod.id("supply_cart"), "main");
    public static final ModelLayerLocation RAM = new ModelLayerLocation(DominionMod.id("battering_ram"), "main");
    public static final ModelLayerLocation TREBUCHET = new ModelLayerLocation(DominionMod.id("trebuchet"), "main");
    public static final ModelLayerLocation CANNON = new ModelLayerLocation(DominionMod.id("cannon"), "main");

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CART, () -> LayerDefinition.create(EngineMeshes.supplyCart(), EngineMeshes.SUPPLYCART_W, EngineMeshes.SUPPLYCART_H));
        e.registerLayerDefinition(RAM, () -> LayerDefinition.create(EngineMeshes.batteringRam(), EngineMeshes.BATTERINGRAM_W, EngineMeshes.BATTERINGRAM_H));
        e.registerLayerDefinition(TREBUCHET, () -> LayerDefinition.create(EngineMeshes.trebuchet(), EngineMeshes.TREBUCHET_W, EngineMeshes.TREBUCHET_H));
        e.registerLayerDefinition(CANNON, () -> LayerDefinition.create(EngineMeshes.cannon(), EngineMeshes.CANNON_W, EngineMeshes.CANNON_H));
    }
}
