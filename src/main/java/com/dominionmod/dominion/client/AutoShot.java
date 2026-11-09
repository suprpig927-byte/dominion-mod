package com.dominionmod.dominion.client;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.debug.Showcase;
import com.dominionmod.dominion.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Automated visual test (only with -Ddominion.autoshot=true): once in a world, build the showcase,
 * take screenshots of the citizens, the buildings, the Realm screen and the flag designer, then quit.
 */
public final class AutoShot {
    private AutoShot() {}

    private static int t = -1;

    public static void init() {
        if (!Boolean.getBoolean("dominion.autoshot")) return;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                msg -> DominionMod.LOGGER.info("AUTOSHOT {} -> {}", name, msg.getString()));
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> job) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var id = mc.player.getUUID();
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(id);
            if (sp != null) job.accept(sp);
        });
    }

    private static void look(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        onServer(mc, sp -> sp.teleportTo((net.minecraft.server.level.ServerLevel) sp.level(), x, y, z, java.util.Set.of(), yaw, pitch, true));
    }

    private static double bx, by, bz;

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        t++;
        if (t == 1) DominionMod.LOGGER.info("AUTOSHOT in world");
        if (t == 100) {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            onServer(mc, Showcase::run);
        }
        if (t == 140) {
            bx = mc.player.getX();
            by = mc.player.getY();
            bz = mc.player.getZ();
        }
        if (t == 220) shot(mc, "citizens");
        if (t == 222) look(mc, bx, by, bz - 4.5, 180f, 5f);
        if (t == 262) shot(mc, "citizens_close");
        if (t == 264) look(mc, bx + 1, by, bz - 1, 0f, 10f); // turn around to see their backs
        if (t == 266) look(mc, bx, by, bz - 13, 0f, 10f);
        if (t == 306) shot(mc, "citizens_back");
        if (t == 307) look(mc, bx, by, bz - 7.5, 180f, 5f);
        if (t == 340) shot(mc, "classes");
        if (t == 342) look(mc, bx, by + 5, bz - 2, 180f, 12f);
        if (t == 370) shot(mc, "tiers");
        if (t == 362) look(mc, bx - 24, by + 1, bz - 13, 180f, 5f);
        if (t == 400) shot(mc, "tier_thatch");
        if (t == 402) look(mc, bx + 24, by + 1, bz - 13, 180f, 5f);
        if (t == 440) shot(mc, "tier_royal");
        if (t == 442) look(mc, bx, by + 13, bz - 16, 180f, 22f);
        if (t == 490) shot(mc, "buildings");
        if (t == 492) look(mc, bx - 26, by + 1, bz - 31, 180f, 5f);
        if (t == 530) shot(mc, "storehouse");
        if (t == 532) look(mc, bx + 26, by + 2, bz - 30, 180f, 5f);
        if (t == 580) shot(mc, "university");
        if (t == 582) look(mc, bx - 12, by + 6, bz - 44, 180f, 25f);
        if (t == 620) shot(mc, "farm_tower");
        if (t == 622) look(mc, bx, by + 0.3, bz - 0.5, 0f, 8f);
        if (t == 660) shot(mc, "armory");
        if (t == 662) look(mc, bx, by + 0.3, bz + 2.5, 0f, 8f);
        if (t == 676) look(mc, bx + 5.5, by + 0.3, bz + 1.0, 0f, 8f);
        if (t == 690) shot(mc, "armory_guns");
        if (t == 710) {
            look(mc, bx, by, bz, 0f, 10f);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (t == 720) shot(mc, "first_person");
        if (t == 721) mc.player.getInventory().setSelectedSlot(1);
        if (t == 728) shot(mc, "flintlock_fp");
        if (t == 729) mc.player.getInventory().setSelectedSlot(8);
        if (t == 732) mc.options.keyUse.setDown(true);
        if (t == 740) shot(mc, "blocking");
        if (t == 742) {
            mc.options.keyUse.setDown(false);
            mc.player.getInventory().setSelectedSlot(0);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        }
        if (t == 770) shot(mc, "third_person");
        if (t == 772) {
            mc.player.getInventory().setSelectedSlot(8);
            mc.options.keyUse.setDown(true);
        }
        if (t == 790) shot(mc, "third_person_block");
        if (t == 792) {
            mc.options.keyUse.setDown(false);
            mc.player.getInventory().setSelectedSlot(0);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            look(mc, bx, by, bz, 180f, 10f);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (t == 820) ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.OPEN));
        if (t == 880) shot(mc, "realm");
        if (t == 881 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(1);
        if (t == 900) shot(mc, "realm_citizens");
        if (t == 901 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(2);
        if (t == 920) shot(mc, "realm_build");
        if (t == 921 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(3);
        if (t == 940) shot(mc, "realm_research");
        if (t == 941 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(4);
        if (t == 960) shot(mc, "realm_diplomacy");
        if (t == 961 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(0);
        if (t == 962) mc.gui.setScreen(new FlagScreen(null, Showcase.FLAG, s -> {}, "Done"));
        if (t == 980) shot(mc, "flag");
        if (t == 1000) {
            mc.gui.setScreen(null);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            look(mc, bx + 6.5, by + 2, bz - 14, 145f, 8f);
        }
        if (t == 1040) shot(mc, "house_corner");
        if (t == 1042) look(mc, bx - 6.5, by + 2, bz - 32, -35f, 8f);
        if (t == 1080) shot(mc, "house_back");
        // the new house designs (north row)
        if (t == 1100) look(mc, bx - 19, by + 3, bz - 45, 180f, 8f);
        if (t == 1140) shot(mc, "designs_west");
        if (t == 1142) look(mc, bx + 19, by + 3, bz - 45, 180f, 8f);
        if (t == 1180) shot(mc, "designs_east");
        // the creatures
        if (t == 1182) look(mc, bx - 20.5, by + 1.2, bz - 4.5, 0f, 6f);
        if (t == 1220) shot(mc, "creatures");
        // the orc village to the east
        if (t == 1222) look(mc, bx + 52, by + 9, bz - 3.5, -90f, 20f);
        if (t == 1280) shot(mc, "orc_village");
        // the wild kingdom and its castle to the south
        if (t == 1282) look(mc, bx, by + 16, bz + 14, 0f, 20f);
        if (t == 1340) shot(mc, "castle");
        if (t == 1342) look(mc, bx, by + 7, bz + 92, 180f, 8f);
        if (t == 1390) shot(mc, "castle_gate");
        // casting a spell
        if (t == 1392) {
            look(mc, bx, by, bz, 180f, -5f);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.player.getInventory().setSelectedSlot(7);
        }
        if (t == 1410) mc.options.keyUse.setDown(true);
        if (t == 1413) mc.options.keyUse.setDown(false);
        if (t == 1416) shot(mc, "spell");
        // horse coats and a close look at the deer
        if (t == 1418) {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            look(mc, bx + 14, by + 1.8, bz - 5.5, 0f, 6f);
        }
        if (t == 1460) shot(mc, "horses");
        if (t == 1462) look(mc, bx - 31.0, by + 1.4, bz + 3.6, 0f, 6f);
        if (t == 1500) shot(mc, "deer");
        // inside the houses, and the centaurs
        if (t == 1502) look(mc, bx - 12, by, bz - 18.0, 180f, 20f);
        if (t == 1540) shot(mc, "interior_cottage");
        if (t == 1542) look(mc, bx - 26, by, bz - 53.0, 180f, 14f);
        if (t == 1580) shot(mc, "interior_longhouse");
        if (t == 1582) look(mc, bx + 0.7, by + 1.0, bz + 12.0, 0f, 4f);
        if (t == 1620) shot(mc, "centaurs");
        // the war camp to the west
        if (t == 1622) look(mc, bx - 81, by + 3.5, bz + 15, 180f, 10f);
        if (t == 1680) shot(mc, "siege_engines");
        if (t == 1682) look(mc, bx - 58, by + 2.0, bz + 9.5, 180f, 7f);
        if (t == 1720) shot(mc, "cavalry");
        if (t == 1722) look(mc, bx - 68, by + 6, bz - 5, 180f, 18f);
        if (t == 1760) shot(mc, "stables");
        if (t == 1762) look(mc, bx - 44.5, by + 2.6, bz + 7, 180f, 6f);
        if (t == 1800) shot(mc, "warlord");
        if (t == 1820) {
            DominionMod.LOGGER.info("AUTOSHOT done");
            mc.stop();
        }
    }
}
