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
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(ModContent.CITIZEN.get(), CitizenRenderer::new));
        modBus.addListener(CitizenModels::register);
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
