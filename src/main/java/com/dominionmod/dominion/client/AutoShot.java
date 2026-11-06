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
        if (t == 308) look(mc, bx, by + 6, bz - 6, 180f, 15f);
        if (t == 360) shot(mc, "buildings");
        if (t == 362) look(mc, bx - 12, by + 1, bz - 12, 180f, 0f);
        if (t == 410) shot(mc, "storehouse");
        if (t == 412) {
            look(mc, bx, by, bz, 180f, 10f);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (t == 440) ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.OPEN));
        if (t == 500) shot(mc, "realm");
        if (t == 501 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(1);
        if (t == 520) shot(mc, "realm_citizens");
        if (t == 521 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(2);
        if (t == 540) shot(mc, "realm_build");
        if (t == 541 && mc.gui.screen() instanceof RealmScreen r) r.debugTab(0);
        if (t == 545) mc.gui.setScreen(new FlagScreen(null, Showcase.FLAG, s -> {}, "Done"));
        if (t == 580) shot(mc, "flag");
        if (t == 600) {
            mc.gui.setScreen(null);
            DominionMod.LOGGER.info("AUTOSHOT done");
            mc.stop();
        }
    }
}
