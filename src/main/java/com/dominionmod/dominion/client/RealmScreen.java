package com.dominionmod.dominion.client;

import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Your kingdom at a glance: people, food, coin, Influence, and a map of the land around you. */
public class RealmScreen extends Screen {
    private static final int W = 340, H = 214, CELL = 11;
    private Payloads.RealmInfo info;

    public RealmScreen(Payloads.RealmInfo info) {
        super(Component.literal("Realm"));
        this.info = info;
    }

    public static void receive(Payloads.RealmInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof RealmScreen open) {
            open.info = info;
            open.rebuildWidgets();
        } else if (info.open()) {
            mc.gui.setScreen(new RealmScreen(info));
        }
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return (this.height - H) / 2; }

    private static void send(int action) {
        ClientPacketDistributor.sendToServer(new Payloads.RealmAction(action));
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        if (info.has()) {
            addRenderableWidget(Button.builder(Component.literal("Claim this chunk (" + info.claimCost() + ")"), b -> send(Payloads.RealmAction.CLAIM))
                    .bounds(l + 10, t + H - 52, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Release this chunk"), b -> send(Payloads.RealmAction.UNCLAIM))
                    .bounds(l + 10, t + H - 28, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal(info.peaceful() ? "Monster spawns: OFF" : "Monster spawns: ON"),
                    b -> send(Payloads.RealmAction.TOGGLE_PEACEFUL)).bounds(l + 166, t + H - 52, 164, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> send(Payloads.RealmAction.REFRESH))
                .bounds(l + 166, t + H - 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(l + 250, t + H - 28, 80, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int color(int dye) {
        return 0xFF000000 | DyeColor.byId(Math.floorMod(dye, 16)).getTextureDiffuseColor();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        int p = info.has() ? color(info.primary()) : 0xFF777777;
        int s = info.has() ? color(info.secondary()) : 0xFF555555;
        g.fill(l, t, l + W, t + H, 0xE0121416);
        g.fill(l, t, l + W, t + 3, p);
        g.fill(l, t + 3, l + W, t + 4, s);

        int x = l + 10, y = t + 10;
        if (!info.has()) {
            g.text(this.font, "You don't rule a kingdom yet.", x, y, 0xFFFFD86B, true);
            g.text(this.font, "Craft a Town Hall and place it to found one,", x, y + 14, 0xFFCCCCCC, false);
            g.text(this.font, "or ask a ruler for an invitation.", x, y + 26, 0xFFCCCCCC, false);
        } else {
            g.text(this.font, "Kingdom of " + info.name(), x, y, p, true);
            y += 16;
            g.text(this.font, String.format("Influence: %.0f  (+%.0f / day)", info.influence(), info.daily()), x, y, 0xFFB8A6FF, false);
            y += 12;
            g.text(this.font, "Land: " + info.chunks() + " chunks   Next claim: " + info.claimCost(), x, y, 0xFFCCCCCC, false);
            y += 12;
            int pop = info.citizens();
            g.text(this.font, "Citizens: " + pop + " / " + info.beds() + " beds", x, y, pop >= info.beds() ? 0xFFFF8080 : 0xFFCCCCCC, false);
            y += 12;
            var jobs = info.jobs();
            for (Job j : Job.values()) {
                int n = j.ordinal() < jobs.size() ? jobs.get(j.ordinal()) : 0;
                if (n == 0) continue;
                g.text(this.font, "  " + (j == Job.UNASSIGNED ? "Unassigned" : j.title + (j.soldier ? "" : "s")) + ": " + n, x, y,
                        j.soldier ? 0xFFFFB070 : 0xFFA0D890, false);
                y += 10;
            }
            y += 4;
            int soldiers = (jobs.size() > 4 ? jobs.get(Job.MILITIA.ordinal()) + jobs.get(Job.ARCHER.ordinal()) : 0);
            g.text(this.font, "Storehouse: " + info.coins() + " coins, " + info.food() + " food", x, y, 0xFFFFD86B, false);
            y += 12;
            int needFood = pop, needCoins = soldiers;
            if (info.food() < needFood) g.text(this.font, "Not enough food for tomorrow!", x, y, 0xFFFF6060, false);
            else if (info.coins() < needCoins) g.text(this.font, "Not enough coin for tomorrow's wages!", x, y, 0xFFFF6060, false);
            else g.text(this.font, "Chests by the Town Hall are your storehouse.", x, y, 0xFF777777, false);
        }

        // Map: 11 x 11 chunks centered on you.
        int n = Payloads.MAP_RADIUS * 2 + 1;
        int mx = l + W - 10 - n * CELL, my = t + 12;
        g.fill(mx - 1, my - 1, mx + n * CELL + 1, my + n * CELL + 1, 0xFF000000);
        for (int i = 0; i < info.map().size() && i < n * n; i++) {
            int v = info.map().get(i);
            int cx = mx + (i % n) * CELL, cy = my + (i / n) * CELL;
            int c = v < 0 ? 0xFF2B3A2B : v >= 100 ? color(v - 100) : color(v) & 0x99FFFFFF;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, c);
            if (v >= 100) g.fill(cx, cy + CELL - 2, cx + CELL - 1, cy + CELL - 1, s);
        }
        int c0 = mx + Payloads.MAP_RADIUS * CELL, r0 = my + Payloads.MAP_RADIUS * CELL;
        g.fill(c0 + 3, r0 + 3, c0 + CELL - 4, r0 + CELL - 4, 0xFFFFFFFF);
        String here = info.here().isEmpty() ? "Here: wilderness" : "Here: " + info.here();
        g.text(this.font, here, mx, my + n * CELL + 4, 0xFFAAAAAA, false);
        g.text(this.font, "North is up", mx, my + n * CELL + 14, 0xFF666666, false);

        if (!info.message().isEmpty()) g.text(this.font, info.message(), l + 10, t + H - 64, 0xFFFFE08A, false);

        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }
}
