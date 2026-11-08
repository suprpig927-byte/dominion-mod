package com.dominionmod.dominion.client.render.wild;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.BreedHorse;
import net.minecraft.client.model.animal.equine.EquineSaddleModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Variant;

/**
 * Draws a breed horse with its own model (feathered Clydesdale legs, the mustang's long mane, the
 * shaggy short-legged pony) and a hand-painted coat per breed and colour. Markings are painted
 * into the coat, so vanilla's marking overlay isn't used. Saddles and armour sit on a matching body.
 */
public class BreedHorseRenderer extends AbstractHorseRenderer<Horse, HorseRenderState, HorseModel> {
    private final BreedHorse.Breed breed;
    private final String prefix;

    public BreedHorseRenderer(EntityRendererProvider.Context ctx, BreedHorse.Breed breed) {
        super(ctx, new HorseModel(ctx.bakeLayer(WildModels.horse(breed, "main"))), new HorseModel(ctx.bakeLayer(WildModels.horse(breed, "baby"))));
        this.breed = breed;
        this.prefix = "textures/entity/horse/" + breed.name().toLowerCase() + "_";
        this.addLayer(new SimpleEquipmentLayer<>(this, ctx.getEquipmentRenderer(), EquipmentClientInfo.LayerType.HORSE_BODY,
                state -> state.bodyArmorItem, new HorseModel(ctx.bakeLayer(WildModels.horse(breed, "armor"))), null, 2));
        this.addLayer(new SimpleEquipmentLayer<>(this, ctx.getEquipmentRenderer(), EquipmentClientInfo.LayerType.HORSE_SADDLE,
                state -> state.saddle, new EquineSaddleModel(ctx.bakeLayer(WildModels.horse(breed, "saddle"))), null, 2));
    }

    @Override
    public Identifier getTextureLocation(HorseRenderState state) {
        return DominionMod.id(prefix + breed.coatFor(state.variant).getSerializedName() + ".png");
    }

    @Override
    public HorseRenderState createRenderState() {
        return new HorseRenderState();
    }

    @Override
    public void extractRenderState(Horse entity, HorseRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = entity.getVariant();
        state.markings = entity.getMarkings();
        state.bodyArmorItem = entity.getBodyArmorItem().copy();
    }

}
