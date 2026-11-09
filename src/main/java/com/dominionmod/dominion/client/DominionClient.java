package com.dominionmod.dominion.client;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.client.render.CitizenModels;
import com.dominionmod.dominion.client.render.CitizenRenderer;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = DominionMod.MODID, dist = Dist.CLIENT)
public class DominionClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(DominionMod.id("dominion"));
    public static final KeyMapping OPEN_REALM = new KeyMapping("key.dominion.open_realm", InputConstants.KEY_K, CATEGORY);

    public DominionClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ModContent.CITIZEN.get(), CitizenRenderer::new);
            e.registerEntityRenderer(ModContent.BULLET_ENTITY.get(), ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 0.5f, false));
            e.registerEntityRenderer(ModContent.DEER.get(), com.dominionmod.dominion.client.render.wild.DeerRenderer::new);
            e.registerEntityRenderer(ModContent.CENTAUR.get(), com.dominionmod.dominion.client.render.wild.CentaurRenderer::new);
            e.registerEntityRenderer(ModContent.ORC.get(), com.dominionmod.dominion.client.render.wild.OrcRenderer::new);
            e.registerEntityRenderer(ModContent.ORC_WARLORD.get(), com.dominionmod.dominion.client.render.wild.OrcRenderer::new);
            e.registerEntityRenderer(ModContent.SUPPLY_CART.get(), com.dominionmod.dominion.client.render.siege.SupplyCartRenderer::new);
            e.registerEntityRenderer(ModContent.BATTERING_RAM.get(), ctx -> new com.dominionmod.dominion.client.render.siege.SiegeRenderer(ctx,
                    com.dominionmod.dominion.client.render.siege.SiegeModels.RAM, "battering_ram", 1.0f));
            e.registerEntityRenderer(ModContent.TREBUCHET.get(), ctx -> new com.dominionmod.dominion.client.render.siege.SiegeRenderer(ctx,
                    com.dominionmod.dominion.client.render.siege.SiegeModels.TREBUCHET, "trebuchet", 1.5f));
            e.registerEntityRenderer(ModContent.CANNON.get(), ctx -> new com.dominionmod.dominion.client.render.siege.SiegeRenderer(ctx,
                    com.dominionmod.dominion.client.render.siege.SiegeModels.CANNON, "cannon", 1.3f));
            e.registerEntityRenderer(ModContent.BOULDER.get(), ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.4f, false));
            e.registerEntityRenderer(ModContent.CANNONBALL_ENTITY.get(), ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 0.9f, false));
            e.registerEntityRenderer(ModContent.CLYDESDALE.get(), ctx -> new com.dominionmod.dominion.client.render.wild.BreedHorseRenderer(ctx, com.dominionmod.dominion.entity.wild.BreedHorse.Breed.CLYDESDALE));
            e.registerEntityRenderer(ModContent.MUSTANG.get(), ctx -> new com.dominionmod.dominion.client.render.wild.BreedHorseRenderer(ctx, com.dominionmod.dominion.entity.wild.BreedHorse.Breed.MUSTANG));
            e.registerEntityRenderer(ModContent.SHETLAND_PONY.get(), ctx -> new com.dominionmod.dominion.client.render.wild.BreedHorseRenderer(ctx, com.dominionmod.dominion.entity.wild.BreedHorse.Breed.SHETLAND));
        });
        modBus.addListener(CitizenModels::register);
        modBus.addListener(com.dominionmod.dominion.client.render.wild.WildModels::register);
        modBus.addListener(com.dominionmod.dominion.client.render.siege.SiegeModels::register);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(OPEN_REALM);
        });
        AutoShot.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (OPEN_REALM.consumeClick()) {
                ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.OPEN));
            }
        });
    }
}
