package com.dominionmod.dominion.client;

import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Structures;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Your kingdom at a glance (Overview), your people and their beds (Citizens), and construction (Build). */
public class RealmScreen extends Screen {
    private static final int W = 400, H = 290, MAP = 176, ROWS = 16;
    private int refreshTicks;
    private static int tab = 0;
    private static int scroll = 0;
    private static int pickedStructure = 0;
    private static int pickedTier = -1;
    private Payloads.RealmInfo info;

    public RealmScreen(Payloads.RealmInfo info) {
        super(Component.literal("Realm"));
        this.info = info;
    }

    public static void receive(Payloads.RealmInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (info.openTab() >= 0) tab = info.openTab();
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

    private static void send(int action, String arg) {
        ClientPacketDistributor.sendToServer(new Payloads.RealmAction(action, arg));
    }

    /** Debug (automated screenshots). */
    public void debugTab(int t) {
        setTab(t);
    }

    private void setTab(int t) {
        tab = t;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        if (info.has()) {
            String[] names = {"Overview", "Citizens", "Build", "Research"};
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                Button b = Button.builder(Component.literal(names[i]), x -> setTab(idx)).bounds(l + 10 + i * 74, t - 18, 72, 18).build();
                b.active = tab != i;
                addRenderableWidget(b);
            }
        } else {
            tab = 0;
        }
        switch (tab) {
            case 1 -> initCitizens(l, t);
            case 2 -> initBuild(l, t);
            case 3 -> initResearch(l, t);
            default -> initOverview(l, t);
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(l + W - 70, t + H - 26, 60, 20).build());
    }

    private void initOverview(int l, int t) {
        if (info.has()) {
            addRenderableWidget(Button.builder(Component.literal("Claim this chunk (" + info.claimCost() + ")"), b -> send(Payloads.RealmAction.CLAIM))
                    .bounds(l + 10, t + H - 74, 190, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Release this chunk"), b -> send(Payloads.RealmAction.UNCLAIM))
                    .bounds(l + 10, t + H - 50, 190, 20).build());
            if (info.ruler()) {
                addRenderableWidget(Button.builder(Component.literal(info.peaceful() ? "Monster spawns: OFF" : "Monster spawns: ON"),
                        b -> send(Payloads.RealmAction.TOGGLE_PEACEFUL)).bounds(l + 10, t + H - 26, 120, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Edit flag"), b -> Minecraft.getInstance().gui.setScreen(
                        new FlagScreen(this, info.flag(), design -> {
                            send(Payloads.RealmAction.SET_FLAG, design);
                            Minecraft.getInstance().gui.setScreen(this);
                        }, "Raise this flag"))).bounds(l + 134, t + H - 26, 66, 20).build());
            }
        }
    }

    private void initCitizens(int l, int t) {
        int n = info.roster().size();
        if (n > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("^"), b -> { scroll = Math.max(0, scroll - ROWS); rebuildWidgets(); })
                    .bounds(l + 10, t + H - 26, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("v"), b -> { scroll = Math.min(Math.max(0, n - ROWS), scroll + ROWS); rebuildWidgets(); })
                    .bounds(l + 44, t + H - 26, 30, 20).build());
        }
    }

    // ------------------------------------------------------------------ research state (from the server)

    private boolean researched(String id) {
        if (id == null) return true;
        for (String r : info.research()) {
            String[] f = r.split("\\|", -1);
            if (f[0].equals(id)) return f.length > 1 && f[1].equals("done");
        }
        return false;
    }

    private int maxTier() {
        int t = 0;
        for (int i = 1; i < com.dominionmod.dominion.build.Palette.TIER_RESEARCH.length; i++)
            if (researched(com.dominionmod.dominion.build.Palette.TIER_RESEARCH[i])) t = i;
        return t;
    }

    private int tierFor(Structures.Structure s) {
        int t = pickedTier < 0 ? maxTier() : pickedTier;
        return Math.max(t, s.minTier());
    }

    /** Why the picked structure/tier can't be built yet (null = it can). */
    private String lockedReason(Structures.Structure s, int tier) {
        if (s.requires() != null && !researched(s.requires())) {
            var t = com.dominionmod.dominion.kingdom.Research.get(s.requires());
            return "Needs research: " + (t == null ? s.requires() : t.name());
        }
        if (tier > maxTier()) {
            var t = com.dominionmod.dominion.kingdom.Research.get(com.dominionmod.dominion.build.Palette.TIER_RESEARCH[tier]);
            return "Needs research: " + (t == null ? "" : t.name());
        }
        return null;
    }

    private void initBuild(int l, int t) {
        List<Structures.Structure> all = new ArrayList<>(Structures.all());
        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(all.get(i).name()), x -> { pickedStructure = idx; rebuildWidgets(); })
                    .bounds(l + 10, t + 24 + i * 13, 100, 12).build();
            b.active = pickedStructure != i;
            addRenderableWidget(b);
        }
        Structures.Structure s = all.get(Math.min(pickedStructure, all.size() - 1));
        int tier = tierFor(s);
        boolean walls = s.kind() == Structures.Kind.WALL;
        if (!walls || s.id().equals("stone_wall")) {
            for (int i = 0; i < com.dominionmod.dominion.build.Palette.TIER_NAMES.length; i++) {
                final int ti = i;
                Button b = Button.builder(Component.literal(com.dominionmod.dominion.build.Palette.TIER_NAMES[i]), x -> { pickedTier = ti; rebuildWidgets(); })
                        .bounds(l + 120 + i * 54, t + 72, 52, 14).build();
                b.active = i != tier && i >= s.minTier() && i <= maxTier();
                addRenderableWidget(b);
            }
        }
        Button take = Button.builder(Component.literal("Take blueprint"), b -> send(Payloads.RealmAction.BLUEPRINT, s.id() + ":" + tier))
                .bounds(l + W - 176, t + H - 26, 100, 20).build();
        take.active = lockedReason(s, tier) == null;
        addRenderableWidget(take);
        List<String> sites = info.sites();
        for (int i = 0; i < sites.size() && i < 3; i++) {
            String id = sites.get(i).split("\\|", -1)[0];
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> send(Payloads.RealmAction.CANCEL_SITE, id))
                    .bounds(l + W - 62, t + 208 + i * 14, 52, 13).build());
        }
    }

    private void initResearch(int l, int t) {
        if (!info.ruler()) return;
        int i = 0;
        boolean busy = info.research().stream().anyMatch(r -> r.contains("|current|"));
        for (var tech : com.dominionmod.dominion.kingdom.Research.all()) {
            String status = statusOf(tech.id());
            if (status.equals("open") && !busy) {
                addRenderableWidget(Button.builder(Component.literal("Research"), b -> send(Payloads.RealmAction.START_RESEARCH, tech.id()))
                        .bounds(l + W - 80, t + 38 + i * 34, 70, 16).build());
            }
            i++;
        }
    }

    private String statusOf(String id) {
        for (String r : info.research()) {
            String[] f = r.split("\\|", -1);
            if (f[0].equals(id) && f.length > 1) return f[1];
        }
        return "locked";
    }

    private int pctOf(String id) {
        for (String r : info.research()) {
            String[] f = r.split("\\|", -1);
            if (f[0].equals(id) && f.length > 2) return parse(f[2]);
        }
        return 0;
    }

    private void drawResearch(GuiGraphicsExtractor g, int l, int t, int p) {
        g.text(this.font, "Research", l + 10, t + 10, p, true);
        String sch = info.scholars() == 0 ? "No Scholars at work, so research is slow. Give a citizen a book."
                : info.scholars() + " Scholar" + (info.scholars() == 1 ? "" : "s") + " at work: research is " + (1 + 2 * info.scholars()) + "x faster.";
        g.text(this.font, sch, l + 10, t + 22, info.scholars() == 0 ? 0xFFFFB070 : 0xFFA0D890, false);
        int i = 0;
        for (var tech : com.dominionmod.dominion.kingdom.Research.all()) {
            int y = t + 38 + i * 34;
            String status = statusOf(tech.id());
            int color = switch (status) {
                case "done" -> 0xFF8BE07A;
                case "current" -> 0xFFFFD86B;
                case "open" -> 0xFFEEEEEE;
                default -> 0xFF777777;
            };
            g.fill(l + 8, y - 2, l + W - 8, y + 30, 0x30FFFFFF);
            g.text(this.font, tech.name(), l + 12, y, color, false);
            String cost = tech.coins() + " coins, " + tech.lapis() + " lapis";
            String right = switch (status) {
                case "done" -> "Researched";
                case "current" -> "Researching... " + pctOf(tech.id()) + "%";
                case "open" -> cost;
                default -> "Needs " + tech.requires().stream().map(r -> {
                    var rt = com.dominionmod.dominion.kingdom.Research.get(r);
                    return rt == null ? r : rt.name();
                }).reduce((a, b) -> a + ", " + b).orElse("");
            };
            g.text(this.font, right, l + 150, y, status.equals("locked") ? 0xFF777777 : 0xFFBBBBBB, false);
            g.text(this.font, tech.unlocks(), l + 12, y + 12, 0xFF999999, false);
            if (status.equals("current")) {
                int w = W - 24, done = w * pctOf(tech.id()) / 100;
                g.fill(l + 12, y + 24, l + 12 + w, y + 27, 0xFF333333);
                g.fill(l + 12, y + 24, l + 12 + done, y + 27, 0xFFFFD86B);
            }
            i++;
        }
        if (!info.ruler()) g.text(this.font, "Only the ruler can start research.", l + 10, t + H - 20, 0xFF888888, false);
        else g.text(this.font, "Research is paid from the storehouse.", l + 10, t + H - 20, 0xFF888888, false);
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
        switch (tab) {
            case 1 -> drawCitizens(g, l, t, p);
            case 2 -> drawBuild(g, l, t, p);
            case 3 -> drawResearch(g, l, t, p);
            default -> drawOverview(g, l, t, p, s);
        }
        if (!info.message().isEmpty()) {
            List<String> lines = wrap(info.message(), tab == 0 ? 34 : 64);
            for (int i = 0; i < lines.size(); i++) g.text(this.font, lines.get(i), l + 10, t + H - 100 + i * 10 - (lines.size() - 1) * 10, 0xFFFFE08A, false);
        }
        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }

    private void drawOverview(GuiGraphicsExtractor g, int l, int t, int p, int s) {
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
            int soldiers = 0;
            for (Job j : Job.values()) {
                int n = j.ordinal() < jobs.size() ? jobs.get(j.ordinal()) : 0;
                if (j.soldier) soldiers += n;
                if (n == 0) continue;
                g.text(this.font, "  " + (j == Job.UNASSIGNED ? "Unassigned" : j.title + (j.soldier ? "" : "s")) + ": " + n, x, y,
                        j.soldier ? 0xFFFFB070 : 0xFFA0D890, false);
                y += 10;
            }
            y += 4;
            g.text(this.font, "Storehouse: " + info.coins() + " coins, " + info.food() + " food", x, y, 0xFFFFD86B, false);
            y += 12;
            if (info.food() < pop) g.text(this.font, "Not enough food for tomorrow!", x, y, 0xFFFF6060, false);
            else if (info.coins() < soldiers) g.text(this.font, "Not enough coin for tomorrow's wages!", x, y, 0xFFFF6060, false);
            else g.text(this.font, "Chests by the Town Hall are your storehouse.", x, y, 0xFF777777, false);
        }

        drawMap(g, l + W - 10 - MAP, t + 10, s);
    }

    /** The live map: real terrain from the server, with each kingdom's land tinted in its color. */
    private void drawMap(GuiGraphicsExtractor g, int mx, int my, int trim) {
        int n = Payloads.MAP_RADIUS * 2 + 1;
        int cell = MAP / n;
        int px = n * cell;
        g.fill(mx - 1, my - 1, mx + px + 1, my + px + 1, 0xFF000000);
        if (RealmMap.ready) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RealmMap.ID, mx, my, 0, 0, px, px, RealmMap.size(), RealmMap.size(),
                    RealmMap.size(), RealmMap.size());
        } else {
            g.text(this.font, "Loading map...", mx + 50, my + px / 2, 0xFF888888, false);
        }
        // Claims: a light wash of the owner's color, with a solid edge where the owner changes.
        var map = info.map();
        for (int i = 0; i < map.size() && i < n * n; i++) {
            int v = map.get(i);
            if (v < 0) continue;
            int col = i % n, row = i / n;
            int cx = mx + col * cell, cy = my + row * cell;
            int c = color(v >= 100 ? v - 100 : v);
            g.fill(cx, cy, cx + cell, cy + cell, (c & 0x00FFFFFF) | 0x38000000);
            int edge = (c & 0x00FFFFFF) | 0xE0000000;
            if (row == 0 || !map.get(i - n).equals(v)) g.fill(cx, cy, cx + cell, cy + 1, edge);
            if (row == n - 1 || !map.get(i + n).equals(v)) g.fill(cx, cy + cell - 1, cx + cell, cy + cell, edge);
            if (col == 0 || !map.get(i - 1).equals(v)) g.fill(cx, cy, cx + 1, cy + cell, edge);
            if (col == n - 1 || !map.get(i + 1).equals(v)) g.fill(cx + cell - 1, cy, cx + cell, cy + cell, edge);
        }
        // You.
        var player = Minecraft.getInstance().player;
        if (player != null && RealmMap.ready) {
            double fx = (player.getX() - RealmMap.x0) / RealmMap.size() * px, fz = (player.getZ() - RealmMap.z0) / RealmMap.size() * px;
            int ppx = mx + (int) fx, ppz = my + (int) fz;
            if (ppx >= mx && ppx < mx + px && ppz >= my && ppz < my + px) {
                g.fill(ppx - 2, ppz - 2, ppx + 3, ppz + 3, 0xFF000000);
                g.fill(ppx - 1, ppz - 1, ppx + 2, ppz + 2, 0xFFFFFFFF);
                float yaw = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                int ax = ppx + (int) Math.round(-Math.sin(yaw) * 5), az = ppz + (int) Math.round(Math.cos(yaw) * 5);
                g.fill(ax - 1, az - 1, ax + 1, az + 1, 0xFFFFFFFF);
            }
        }
        String here = info.here().isEmpty() ? "Here: wilderness" : "Here: " + info.here();
        g.text(this.font, here, mx, my + px + 4, 0xFFAAAAAA, false);
        g.text(this.font, "North is up", mx + px - this.font.width("North is up"), my + px + 4, 0xFF666666, false);
    }

    @Override
    public void tick() {
        super.tick();
        // Keep the map live while it's on screen.
        if (tab == 0 && info.has() && ++refreshTicks % 40 == 0) send(Payloads.RealmAction.MAP);
    }

    private void drawCitizens(GuiGraphicsExtractor g, int l, int t, int p) {
        int x = l + 10, y = t + 10;
        g.text(this.font, "Citizens of " + info.name(), x, y, p, true);
        y += 14;
        g.text(this.font, "Name", x, y, 0xFF888888, false);
        g.text(this.font, "Job", x + 110, y, 0xFF888888, false);
        g.text(this.font, "Bed", x + 200, y, 0xFF888888, false);
        y += 12;
        List<String> roster = info.roster();
        if (roster.isEmpty()) g.text(this.font, "Nobody yet. Recruit villagers on your land with 5 Gold Coins.", x, y, 0xFFAAAAAA, false);
        for (int i = scroll; i < roster.size() && i < scroll + ROWS; i++) {
            String[] r = roster.get(i).split("\\|", -1);
            if (r.length < 3) continue;
            g.text(this.font, r[0], x, y, 0xFFEEEEEE, false);
            g.text(this.font, r[1], x + 110, y, 0xFFA0D890, false);
            g.text(this.font, r[2], x + 200, y, r[2].equals("no bed") ? 0xFFFF8080 : 0xFFCCCCCC, false);
            y += 12;
        }
        g.text(this.font, "To move someone to another bed: select them with the Command Baton,", l + 10, t + H - 58, 0xFF888888, false);
        g.text(this.font, "then right-click the bed with it. Whoever slept there swaps beds.", l + 10, t + H - 46, 0xFF888888, false);
    }

    private void drawBuild(GuiGraphicsExtractor g, int l, int t, int p) {
        g.text(this.font, "Construction", l + 10, t + 10, p, true);
        List<Structures.Structure> all = new ArrayList<>(Structures.all());
        Structures.Structure s = all.get(Math.min(pickedStructure, all.size() - 1));
        int x = l + 120, y = t + 24;
        int tier = tierFor(s);
        g.text(this.font, s.name() + (s.kind() == Structures.Kind.WALL ? "" : "  (" + s.width() + " x " + s.depth() + ", " + (s.height() - s.sink()) + " tall)"),
                x, y, 0xFFFFD86B, false);
        y += 12;
        for (String line : wrap(s.description(), 38)) {
            g.text(this.font, line, x, y, 0xFFCCCCCC, false);
            y += 10;
        }
        y = t + 92;
        String why = lockedReason(s, tier);
        if (why != null) {
            g.text(this.font, why, x, y, 0xFFFF7070, false);
            y += 12;
        }
        g.text(this.font, (s.kind() == Structures.Kind.WALL ? "Materials per block of wall" : "Materials")
                + (s.id().equals("palisade") ? ":" : " (" + com.dominionmod.dominion.build.Palette.tierName(tier) + "):"), x, y, 0xFF888888, false);
        y += 10;
        int col = 0;
        for (Map.Entry<Item, Integer> e : Plans.materials(s, tier).entrySet()) {
            String name = new ItemStack(e.getKey()).getHoverName().getString();
            if (name.endsWith(" Wool")) name = "Wool (any)";
            if (name.endsWith(" Bed")) name = "Bed (any)";
            if (name.endsWith(" Carpet")) name = "Carpet (any)";
            g.text(this.font, e.getValue() + " " + name, x + col * 118, y, 0xFFDDDDDD, false);
            col++;
            if (col == 2) {
                col = 0;
                y += 10;
            }
        }
        g.text(this.font, "To upgrade a finished building, sneak + right-click the sign by its door.",
                l + 10, t + 186, 0xFF777777, false);

        List<String> sites = info.sites();
        int sy = t + 208;
        g.text(this.font, sites.isEmpty() ? "No construction sites. Mark one with a blueprint." : "Construction sites:", l + 10, sy - 10, 0xFF888888, false);
        for (int i = 0; i < sites.size() && i < 3; i++) {
            String[] r = sites.get(i).split("\\|", -1);
            if (r.length < 6) continue;
            int done = parse(r[2]), total = Math.max(1, parse(r[3]));
            g.text(this.font, r[1] + " at " + r[5] + "  " + (100 * done / total) + "%  " + r[4], l + 10, sy + 2 + i * 14, 0xFFDDDDDD, false);
        }
        if (sites.size() > 3) g.text(this.font, "+" + (sites.size() - 3) + " more", l + 10, sy + 44, 0xFF888888, false);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(w);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
