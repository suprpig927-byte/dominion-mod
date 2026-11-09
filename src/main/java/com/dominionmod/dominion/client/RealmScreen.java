package com.dominionmod.dominion.client;

import com.dominionmod.dominion.build.Palette;
import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Structures;
import com.dominionmod.dominion.client.ui.Theme;
import com.dominionmod.dominion.client.ui.WoodButton;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.kingdom.Research;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Realm: your kingdom at a glance (Overview), your people (Citizens), construction (Build),
 * the tech tree (Research), workshops (Industry), towers and threats (Defense) and other kingdoms
 * (Diplomacy). Parchment and wood, see {@link Theme}.
 */
public class RealmScreen extends Screen {
    private static final int W = 440, H = 296, MAP = 154;
    public static final int OVERVIEW = 0, CITIZENS = 1, BUILD = 2, RESEARCH = 3, INDUSTRY = 4, DEFENSE = 5, DIPLOMACY = 6;
    private static final String[] TABS = {"Overview", "Citizens", "Build", "Research", "Industry", "Defense", "Diplomacy"};
    private int refreshTicks;
    private static int tab = 0;
    private static int scroll = 0;
    private static String pickedStructure = "cottage";
    private static int pickedCategory = 0;
    private static int pickedTier = -1;
    private Payloads.RealmInfo info;
    private final Map<Item, Integer> stock = new HashMap<>();

    public RealmScreen(Payloads.RealmInfo info) {
        super(Component.literal("Realm"));
        setInfo(info);
    }

    private void setInfo(Payloads.RealmInfo info) {
        this.info = info;
        stock.clear();
        for (String row : info.stock()) {
            String[] f = row.split("\\|", -1);
            Identifier id = Identifier.tryParse(f[0]);
            if (id != null && f.length > 1) stock.put(BuiltInRegistries.ITEM.getValue(id), parse(f[1]));
        }
    }

    public static void receive(Payloads.RealmInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (info.openTab() >= 0) tab = info.openTab();
        if (mc.gui.screen() instanceof RealmScreen open) {
            open.setInfo(info);
            open.rebuildWidgets();
        } else if (info.open()) {
            mc.gui.setScreen(new RealmScreen(info));
        }
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return Math.max(20, (this.height - H) / 2 + 8); }

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

    private WoodButton button(String label, net.minecraft.client.gui.components.Button.OnPress press, int x, int y, int w, int h) {
        return addRenderableWidget(WoodButton.of(label, press, x, y, w, h));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int l = left(), t = top();
        if (info.has()) {
            int tw = (W - 16) / TABS.length;
            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                addRenderableWidget(WoodButton.tab(TABS[i], x -> setTab(idx), l + 8 + i * tw, t - 15, tw - 2, 16, tab == i));
            }
        } else {
            tab = 0;
        }
        switch (tab) {
            case CITIZENS -> initCitizens(l, t);
            case BUILD -> initBuild(l, t);
            case RESEARCH -> initResearch(l, t);
            case INDUSTRY -> initIndustry(l, t);
            case DEFENSE -> {}
            case DIPLOMACY -> initDiplomacy(l, t);
            default -> initOverview(l, t);
        }
        button("Close", b -> onClose(), l + W - 66, t + H - 28, 54, 18);
    }

    private void pager(int l, int t, int n, int page) {
        if (n <= page) return;
        button("<", b -> { scroll = Math.max(0, scroll - page); rebuildWidgets(); }, l + 12, t + H - 28, 22, 18).active = scroll > 0;
        button(">", b -> { scroll = Math.min(((n - 1) / page) * page, scroll + page); rebuildWidgets(); }, l + 36, t + H - 28, 22, 18).active = scroll + page < n;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int color(int dye) {
        return 0xFF000000 | DyeColor.byId(Math.floorMod(dye, 16)).getTextureDiffuseColor();
    }

    /** A kingdom colour dark enough to read on parchment. */
    private static int inkOf(int dye) {
        int c = color(dye);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        while (r * 3 + g * 6 + b > 900) {
            r = r * 3 / 4;
            g = g * 3 / 4;
            b = b * 3 / 4;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        Theme.backdrop(g, this.width, this.height);
        Theme.panel(g, l, t, W, H);
        if (info.has()) {
            // the kingdom's colours along the top edge of the parchment
            g.fill(l + 10, t + 10, l + W - 10, t + 12, color(info.primary()));
            g.fill(l + 10, t + 12, l + W - 10, t + 13, color(info.secondary()));
        }
        switch (tab) {
            case CITIZENS -> drawCitizens(g, l, t);
            case BUILD -> drawBuild(g, l, t, mouseX, mouseY);
            case RESEARCH -> drawResearch(g, l, t, mouseX, mouseY);
            case INDUSTRY -> drawIndustry(g, l, t);
            case DEFENSE -> drawDefense(g, l, t);
            case DIPLOMACY -> drawDiplomacy(g, l, t);
            default -> drawOverview(g, l, t, mouseX, mouseY);
        }
        if (!info.message().isEmpty()) {
            List<String> lines = Theme.wrap(info.message(), W - 150);
            int y = t + H - 30 - lines.size() * 10;
            if (tab == OVERVIEW || tab == BUILD) y = t + H - 30;
            int x = tab == OVERVIEW ? l + 216 : l + 70;
            if (tab == OVERVIEW) lines = Theme.wrap(info.message(), W - 300);
            for (int i = 0; i < lines.size(); i++) Theme.text(g, lines.get(i), x, y + i * 10, Theme.GOLD);
        }
        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == OVERVIEW && info.has() && ++refreshTicks % 40 == 0) send(Payloads.RealmAction.MAP);
    }

    // ------------------------------------------------------------------ overview

    private void initOverview(int l, int t) {
        if (!info.has()) return;
        int y = t + H - 74;
        button("Claim this chunk (" + info.claimCost() + ")", b -> send(Payloads.RealmAction.CLAIM), l + 14, y, 190, 18);
        button("Release this chunk", b -> send(Payloads.RealmAction.UNCLAIM), l + 14, y + 22, 190, 18);
        if (info.ruler()) {
            button(info.peaceful() ? "Monsters: OFF" : "Monsters: ON", b -> send(Payloads.RealmAction.TOGGLE_PEACEFUL), l + 14, y + 44, 92, 18);
            button("Edit flag", b -> Minecraft.getInstance().gui.setScreen(new FlagScreen(this, info.flag(), design -> {
                send(Payloads.RealmAction.SET_FLAG, design);
                Minecraft.getInstance().gui.setScreen(this);
            }, "Raise this flag")), l + 110, y + 44, 94, 18);
        }
    }

    private void stat(GuiGraphicsExtractor g, int x, int y, String icon, ItemStack item, String label, String value, int valueColor) {
        Theme.inset(g, x, y, 96, 30);
        if (item.isEmpty()) Theme.icon(g, icon, x + 5, y + 9);
        else g.item(item, x + 3, y + 7);
        Theme.text(g, label, x + 22, y + 5, Theme.INK_SOFT);
        Theme.text(g, value, x + 22, y + 17, valueColor);
    }

    private void drawOverview(GuiGraphicsExtractor g, int l, int t, int mx, int my) {
        int x = l + 14, y = t + 18;
        if (!info.has()) {
            Theme.ribbon(g, "No Kingdom", l + W / 2, t + 18);
            y = t + 44;
            y = Theme.paragraph(g, "You don't rule a kingdom yet. Craft a Town Hall and place it to found one, or ask a ruler for an invitation.",
                    x, y, 200, Theme.INK);
            drawMap(g, l + W - 14 - MAP, t + 20);
            return;
        }
        Theme.ribbon(g, "Kingdom of " + info.name(), l + 112, t + 16);
        y = t + 38;
        int pop = info.citizens();
        int soldiers = 0;
        var jobs = info.jobs();
        for (Job j : Job.values()) if (j.ordinal() < jobs.size() && j.soldier) soldiers += jobs.get(j.ordinal());
        stat(g, x, y, "crown", ItemStack.EMPTY, "Influence", String.format("%.0f (+%.0f/day)", info.influence(), info.daily()), Theme.PURPLE);
        stat(g, x + 100, y, "land", ItemStack.EMPTY, "Land", info.chunks() + " chunks", Theme.GREEN);
        stat(g, x, y + 34, "people", ItemStack.EMPTY, "People", pop + " / " + info.beds() + " beds", pop >= info.beds() ? Theme.RED : Theme.INK);
        stat(g, x + 100, y + 34, "swords", ItemStack.EMPTY, "Soldiers", String.valueOf(soldiers), Theme.INK);
        stat(g, x, y + 68, "", new ItemStack(ModContent.GOLD_COIN.get()), "Treasury", info.coins() + " coins", Theme.GOLD);
        int days = pop == 0 ? 99 : info.food() / Math.max(1, pop);
        stat(g, x + 100, y + 68, "", new ItemStack(Items.BREAD), "Food", info.food() + (pop > 0 ? " (" + Math.min(days, 99) + "d)" : ""),
                info.food() < pop ? Theme.RED : Theme.INK);

        // who does what
        y += 104;
        Theme.heading(g, "Work", x, y, 196);
        y += 14;
        int col = 0;
        for (Job j : Job.values()) {
            int n = j.ordinal() < jobs.size() ? jobs.get(j.ordinal()) : 0;
            if (n == 0) continue;
            String name = (j == Job.UNASSIGNED ? "Idle" : j.title) + ": " + n;
            Theme.text(g, name, x + col * 100, y, j.soldier ? 0xFF8A4A10 : j == Job.UNASSIGNED ? Theme.INK_FAINT : Theme.GREEN);
            col++;
            if (col == 2) {
                col = 0;
                y += 10;
            }
            if (y > t + H - 88) break;
        }
        // warnings
        int threats = 0;
        for (String d : info.defense()) if (d.startsWith("threat|")) threats++;
        int wy = t + 38;
        int wx = l + W - 14 - MAP;
        drawMap(g, wx, t + 20);
        wy = t + 20 + MAP + 18;
        List<String[]> warns = new ArrayList<>();
        if (threats > 0) warns.add(new String[]{"warn", threats + (threats == 1 ? " army marches" : " armies march") + " on you! See Defense."});
        if (info.food() < pop) warns.add(new String[]{"warn", "Not enough food for tomorrow."});
        else if (info.coins() < soldiers) warns.add(new String[]{"warn", "Not enough coin for tomorrow's wages."});
        if (pop >= info.beds()) warns.add(new String[]{"warn", "Every bed is taken: build houses to grow."});
        for (String[] w : warns) {
            Theme.icon(g, w[0], wx, wy - 2);
            for (String line : Theme.wrap(w[1], MAP - 15)) {
                Theme.text(g, line, wx + 15, wy, Theme.RED);
                wy += 10;
            }
            wy += 2;
        }
    }

    /** The live map: real terrain from the server, each kingdom's land tinted, and your watchtowers. */
    private void drawMap(GuiGraphicsExtractor g, int mx, int my) {
        int n = Payloads.MAP_RADIUS * 2 + 1;
        int cell = MAP / n;
        int px = n * cell;
        g.fill(mx - 3, my - 3, mx + px + 3, my + px + 3, 0xFF3A2410);
        g.fill(mx - 1, my - 1, mx + px + 1, my + px + 1, 0xFFC9A86A);
        if (RealmMap.ready) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RealmMap.ID, mx, my, 0, 0, px, px, RealmMap.size(), RealmMap.size(),
                    RealmMap.size(), RealmMap.size());
        } else {
            g.fill(mx, my, mx + px, my + px, 0xFFD8C49A);
            g.centeredText(this.font, "Loading map...", mx + px / 2, my + px / 2, Theme.INK_SOFT);
        }
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
        if (RealmMap.ready) {
            for (String d : info.defense()) {
                String[] f = d.split("\\|", -1);
                if (!f[0].equals("tower") || f.length < 9) continue;
                double fx = (parse(f[2]) - RealmMap.x0) / (double) RealmMap.size() * px, fz = (parse(f[3]) - RealmMap.z0) / (double) RealmMap.size() * px;
                int tx = mx + (int) fx, tz = my + (int) fz;
                if (tx < mx || tz < my || tx >= mx + px || tz >= my + px) continue;
                int r = parse(f[8]) * 16 * px / RealmMap.size() + cell / 2;
                int x0 = Math.max(mx, tx - r), z0 = Math.max(my, tz - r), x1 = Math.min(mx + px, tx + r), z1 = Math.min(my + px, tz + r);
                if (x1 > x0 && z1 > z0) g.outline(x0, z0, x1 - x0, z1 - z0, 0x90FFE070);
                Theme.icon(g, "tower", tx - 6, tz - 6);
            }
        }
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
        Theme.text(g, here, mx, my + px + 5, Theme.INK_SOFT);
        Theme.textRight(g, "N ^", mx + px, my + px + 5, Theme.INK_FAINT);
    }

    // ------------------------------------------------------------------ citizens

    private static final int ROWS = 17;

    private void initCitizens(int l, int t) {
        pager(l, t, info.roster().size(), ROWS);
    }

    private void drawCitizens(GuiGraphicsExtractor g, int l, int t) {
        int x = l + 14, y = t + 18;
        List<String> roster = info.roster();
        Theme.ribbon(g, "Citizens of " + info.name(), l + W / 2, t + 16);
        y = t + 38;
        Theme.text(g, "Name", x + 4, y, Theme.INK_SOFT);
        Theme.text(g, "Job", x + 104, y, Theme.INK_SOFT);
        Theme.text(g, "Works at", x + 194, y, Theme.INK_SOFT);
        Theme.text(g, "Bed", x + 304, y, Theme.INK_SOFT);
        g.fill(x, y + 10, l + W - 14, y + 11, 0x40553311);
        y += 14;
        if (roster.isEmpty()) Theme.paragraph(g, "Nobody yet. Recruit villagers standing on your land with 5 Gold Coins.", x + 4, y, W - 40, Theme.INK);
        for (int i = scroll; i < roster.size() && i < scroll + ROWS; i++) {
            String[] r = roster.get(i).split("\\|", -1);
            if (r.length < 3) continue;
            if ((i - scroll) % 2 == 0) g.fill(x, y - 2, l + W - 14, y + 9, 0x18553311);
            boolean soldier = r.length > 4 && Boolean.parseBoolean(r[4]);
            Theme.text(g, this.font.plainSubstrByWidth(r[0], 96), x + 4, y, Theme.INK);
            Theme.text(g, r[1], x + 104, y, soldier ? 0xFF8A4A10 : r[1].equals("Citizen") ? Theme.INK_FAINT : Theme.GREEN);
            Theme.text(g, r.length > 3 && !r[3].isEmpty() ? this.font.plainSubstrByWidth(r[3], 104) : "-", x + 194, y, Theme.INK_SOFT);
            Theme.text(g, r[2], x + 304, y, r[2].equals("no bed") ? Theme.RED : Theme.INK_SOFT);
            y += 11;
        }
        Theme.text(g, roster.size() + " citizens. Move a bed: select with the Command Baton, click a bed.", l + 66, t + H - 22, Theme.INK_FAINT);
    }

    // ------------------------------------------------------------------ build

    private static final String[] CATEGORIES = {"Homes", "Work", "Civic", "Military", "Walls"};

    private static int categoryOf(Structures.Structure s) {
        if (s.kind() == Structures.Kind.WALL) return 4;
        return switch (s.id()) {
            case "cottage", "longhouse", "farmhouse", "townhouse", "castle" -> 0;
            case "farm", "storehouse", "market_stall", "mill", "bakery", "butcher", "mine", "lumber_mill", "harbor", "smithy", "stables" -> 1;
            case "barracks", "lookout", "watchtower", "fortified_tower", "archery_range", "wall_gate" -> 3;
            default -> 2;
        };
    }

    private List<Structures.Structure> inCategory(int cat) {
        List<Structures.Structure> out = new ArrayList<>();
        for (Structures.Structure s : Structures.all()) if (categoryOf(s) == cat) out.add(s);
        return out;
    }

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
        for (int i = 1; i < Palette.TIER_RESEARCH.length; i++) if (researched(Palette.TIER_RESEARCH[i])) t = i;
        return t;
    }

    private int tierFor(Structures.Structure s) {
        int t = pickedTier < 0 ? maxTier() : pickedTier;
        return Math.max(t, s.minTier());
    }

    private String lockedReason(Structures.Structure s, int tier) {
        if (s.requires() != null && !researched(s.requires())) {
            var t = Research.get(s.requires());
            return "Needs research: " + (t == null ? s.requires() : t.name());
        }
        if (tier > maxTier()) {
            var t = Research.get(Palette.TIER_RESEARCH[tier]);
            return "Needs research: " + (t == null ? "" : t.name());
        }
        return null;
    }

    private Structures.Structure picked() {
        Structures.Structure s = Structures.get(pickedStructure);
        if (s == null || categoryOf(s) != pickedCategory) {
            List<Structures.Structure> list = inCategory(pickedCategory);
            s = list.isEmpty() ? Structures.get("cottage") : list.get(0);
            pickedStructure = s.id();
        }
        return s;
    }

    private void initBuild(int l, int t) {
        int cw = 78;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            addRenderableWidget(WoodButton.of(CATEGORIES[i], b -> { pickedCategory = idx; pickedStructure = ""; rebuildWidgets(); },
                    l + 14 + i * (cw + 2), t + 18, cw, 16).selected(pickedCategory == i));
        }
        Structures.Structure s = picked();
        List<Structures.Structure> list = inCategory(pickedCategory);
        for (int i = 0; i < list.size(); i++) {
            Structures.Structure st = list.get(i);
            boolean locked = st.requires() != null && !researched(st.requires());
            addRenderableWidget(WoodButton.of((locked ? "* " : "") + st.name(), b -> { pickedStructure = st.id(); rebuildWidgets(); },
                    l + 14, t + 40 + i * 17, 118, 15).selected(st == s));
        }
        int tier = tierFor(s);
        boolean walls = s.kind() == Structures.Kind.WALL;
        if (!walls || s.id().equals("stone_wall")) {
            for (int i = 0; i < Palette.TIER_NAMES.length; i++) {
                final int ti = i;
                WoodButton b = addRenderableWidget(WoodButton.of(Palette.TIER_NAMES[i], x -> { pickedTier = ti; rebuildWidgets(); },
                        l + 140 + i * 57, t + 112, 55, 15).selected(i == tier));
                b.active = i >= s.minTier() && i <= maxTier();
            }
        }
        WoodButton take = button("Take blueprint", b -> send(Payloads.RealmAction.BLUEPRINT, s.id() + ":" + tier), l + W - 178, t + H - 28, 108, 18);
        take.active = lockedReason(s, tier) == null;
        List<String> sites = info.sites();
        for (int i = 0; i < sites.size() && i < 2; i++) {
            String id = sites.get(i).split("\\|", -1)[0];
            button("Cancel", b -> send(Payloads.RealmAction.CANCEL_SITE, id), l + W - 66, t + 223 + i * 16, 50, 14);
        }
    }

    private void drawBuild(GuiGraphicsExtractor g, int l, int t, int mx, int my) {
        Structures.Structure s = picked();
        int x = l + 140, y = t + 40;
        int tier = tierFor(s);
        Theme.inset(g, x - 4, y - 4, W - 150, 172);
        Theme.text(g, s.name(), x, y, Theme.INK);
        if (s.kind() != Structures.Kind.WALL)
            Theme.textRight(g, s.width() + " x " + s.depth() + ", " + (s.height() - s.sink()) + " tall", l + W - 18, y, Theme.INK_SOFT);
        y += 12;
        y = Theme.paragraph(g, s.description(), x, y, W - 162, Theme.INK_SOFT);
        y = Math.max(y, t + 100);
        String why = lockedReason(s, tier);
        Theme.text(g, walls(s) ? "Tier:" : "Build in:", x, t + 102, Theme.INK_SOFT);
        y = t + 132;
        if (why != null) {
            Theme.icon(g, "lock", x, y - 2);
            Theme.text(g, why, x + 15, y, Theme.RED);
            y += 13;
        }
        Theme.text(g, (s.kind() == Structures.Kind.WALL ? "Materials per block of wall" : "Materials")
                + (s.id().equals("palisade") ? "" : " (" + Palette.tierName(tier) + ")") + "  - green: in your storehouse", x, y, Theme.INK_SOFT);
        y += 11;
        int col = 0;
        for (Map.Entry<Item, Integer> e : Plans.materials(s, tier).entrySet()) {
            int have = haveOf(e.getKey());
            int cx = x + col * 96;
            g.item(new ItemStack(e.getKey()), cx, y);
            String name = new ItemStack(e.getKey()).getHoverName().getString();
            if (name.endsWith(" Wool")) name = "Wool";
            if (name.endsWith(" Bed")) name = "Bed";
            if (name.endsWith(" Carpet")) name = "Carpet";
            Theme.text(g, e.getValue() + "", cx + 18, y + 1, have >= e.getValue() ? Theme.GREEN : Theme.RED);
            Theme.text(g, this.font.plainSubstrByWidth(name, 58), cx + 18, y + 9, Theme.INK_FAINT);
            if (Theme.over(mx, my, cx, y, 92, 18)) Theme.tooltip(g, List.of(name + ": need " + e.getValue(), "In your storehouse: " + have), mx, my);
            col++;
            if (col == 3) {
                col = 0;
                y += 20;
                if (y > t + 184) break;
            }
        }
        List<String> sites = info.sites();
        int sy = t + 224;
        Theme.heading(g, sites.isEmpty() ? "No construction sites: take a blueprint and mark one." : "Construction sites", l + 14, sy - 13, W - 28);
        for (int i = 0; i < sites.size() && i < 2; i++) {
            String[] r = sites.get(i).split("\\|", -1);
            if (r.length < 6) continue;
            int done = parse(r[2]), total = Math.max(1, parse(r[3]));
            int pct = 100 * done / total;
            Theme.text(g, r[1] + " at " + r[5], l + 16, sy + 2 + i * 16, Theme.INK);
            Theme.bar(g, l + 160, sy + 3 + i * 16, 60, pct, "gold");
            Theme.text(g, pct + "%  " + this.font.plainSubstrByWidth(r[4], 130), l + 226, sy + 2 + i * 16, Theme.INK_SOFT);
        }
        if (sites.size() > 2) Theme.text(g, "+" + (sites.size() - 2) + " more", l + 16, sy + 34, Theme.INK_FAINT);
    }

    private static boolean walls(Structures.Structure s) {
        return s.kind() == Structures.Kind.WALL;
    }

    private int haveOf(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_wool") || path.endsWith("_bed") || path.endsWith("_carpet")) {
            String suffix = path.substring(path.lastIndexOf('_'));
            int n = 0;
            for (var e : stock.entrySet()) if (BuiltInRegistries.ITEM.getKey(e.getKey()).getPath().endsWith(suffix)) n += e.getValue();
            return n;
        }
        return stock.getOrDefault(item, 0);
    }

    // ------------------------------------------------------------------ research

    private static final int PAGE = 8;

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

    private List<Research.Tech> techs() {
        return new ArrayList<>(Research.all());
    }

    private void initResearch(int l, int t) {
        List<Research.Tech> all = techs();
        if (scroll >= all.size()) scroll = 0;
        pager(l, t, all.size(), PAGE);
        if (!info.ruler()) return;
        boolean busy = info.research().stream().anyMatch(r -> r.contains("|current|"));
        for (int i = scroll; i < Math.min(all.size(), scroll + PAGE); i++) {
            Research.Tech tech = all.get(i);
            int slot = i - scroll;
            int cx = l + 14 + (slot % 2) * 208, cy = t + 50 + (slot / 2) * 52;
            if (statusOf(tech.id()).equals("open") && !busy)
                button("Research", b -> send(Payloads.RealmAction.START_RESEARCH, tech.id()), cx + 140, cy + 31, 60, 15);
        }
    }

    private void drawResearch(GuiGraphicsExtractor g, int l, int t, int mx, int my) {
        Theme.ribbon(g, "Research", l + W / 2, t + 16);
        String sch = info.scholars() == 0 ? "No Scholars at work, so research is slow. Give a citizen a book."
                : info.scholars() + " Scholar" + (info.scholars() == 1 ? "" : "s") + " at work: research goes " + (1 + 2 * info.scholars()) + "x faster.";
        Theme.text(g, sch, l + 16, t + 36, info.scholars() == 0 ? Theme.RED : Theme.GREEN);
        List<Research.Tech> all = techs();
        for (int i = scroll; i < Math.min(all.size(), scroll + PAGE); i++) {
            Research.Tech tech = all.get(i);
            int slot = i - scroll;
            int cx = l + 14 + (slot % 2) * 208, cy = t + 50 + (slot / 2) * 52;
            String status = statusOf(tech.id());
            if (status.equals("current")) Theme.insetHot(g, cx, cy, 204, 49);
            else Theme.inset(g, cx, cy, 204, 49);
            String icon = switch (status) {
                case "done" -> "check";
                case "locked" -> "lock";
                case "current" -> "hammer";
                default -> "scroll";
            };
            Theme.icon(g, icon, cx + 5, cy + 5);
            Theme.text(g, tech.name(), cx + 20, cy + 6, status.equals("locked") ? Theme.INK_FAINT : Theme.INK);
            List<String> lines = Theme.wrap(tech.unlocks(), 192);
            for (int k = 0; k < Math.min(2, lines.size()); k++) Theme.text(g, lines.get(k), cx + 6, cy + 16 + k * 9, Theme.INK_SOFT);
            if (Theme.over(mx, my, cx, cy, 204, 49) && lines.size() > 2) Theme.tooltip(g, Theme.wrap(tech.unlocks(), 220), mx, my);
            int by = cy + 37;
            switch (status) {
                case "done" -> Theme.text(g, "Researched", cx + 6, by, Theme.GREEN);
                case "current" -> {
                    Theme.bar(g, cx + 6, by + 2, 120, pctOf(tech.id()), "gold");
                    Theme.text(g, pctOf(tech.id()) + "%", cx + 132, by, Theme.GOLD);
                }
                case "open" -> {
                    g.item(new ItemStack(ModContent.GOLD_COIN.get()), cx + 4, by - 4);
                    Theme.text(g, String.valueOf(tech.coins()), cx + 21, by, Theme.INK);
                    g.item(new ItemStack(Items.LAPIS_LAZULI), cx + 44, by - 4);
                    Theme.text(g, String.valueOf(tech.lapis()), cx + 61, by, Theme.INK);
                    Theme.text(g, tech.days() + "d", cx + 86, by, Theme.INK_FAINT);
                }
                default -> Theme.text(g, this.font.plainSubstrByWidth("Needs " + tech.requires().stream().map(r -> {
                    var rt = Research.get(r);
                    return rt == null ? r : rt.name();
                }).reduce((a, b) -> a + ", " + b).orElse(""), 190), cx + 6, by, Theme.INK_FAINT);
            }
        }
        Theme.text(g, (scroll / PAGE + 1) + " / " + ((all.size() - 1) / PAGE + 1), l + 64, t + H - 23, Theme.INK_SOFT);
        Theme.text(g, info.ruler() ? "Paid from the storehouse." : "Only the ruler can start research.", l + 110, t + H - 23, Theme.INK_FAINT);
    }

    // ------------------------------------------------------------------ industry

    private static final int SHOPS_PAGE = 5;

    private void initIndustry(int l, int t) {
        List<String> rows = info.industry();
        pager(l, t, rows.size(), SHOPS_PAGE);
        if (!info.ruler()) return;
        for (int i = scroll; i < Math.min(rows.size(), scroll + SHOPS_PAGE); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            if (f.length < 10 || f[2].equals("-")) continue;
            boolean open = Boolean.parseBoolean(f[5]);
            String id = f[0];
            int y = t + 40 + (i - scroll) * 44;
            button(open ? "Close shop" : "Reopen", b -> send(Payloads.RealmAction.TOGGLE_SHOP, id), l + W - 88, y + 4, 70, 15);
        }
    }

    private void drawIndustry(GuiGraphicsExtractor g, int l, int t) {
        Theme.ribbon(g, "Industry", l + W / 2, t + 16);
        List<String> rows = info.industry();
        if (rows.isEmpty()) {
            int y = Theme.paragraph(g, "No workshops yet. Build a Mine, Lumber Mill, Mill, Bakery, Butcher, Harbor, Tavern or Chapel from the Build tab "
                    + "(under Work and Civic). Each hires an idle citizen - anyone with empty hands - and makes goods for your storehouse "
                    + "every minute they're at work in daylight.", l + 18, t + 44, W - 40, Theme.INK);
            Theme.paragraph(g, "An Archery Range needs no hands: every archer in the kingdom hits harder.", l + 18, y + 8, W - 40, Theme.INK_SOFT);
            return;
        }
        int workers = 0;
        for (String r : rows) {
            String[] f = r.split("\\|", -1);
            if (f.length > 3 && !f[3].isEmpty()) workers += f[3].split(", ").length;
        }
        Theme.text(g, rows.size() + " workshops, " + workers + " workers. Workshops hire idle citizens (empty hands, no class).", l + 16, t + 30, Theme.INK_SOFT);
        for (int i = scroll; i < Math.min(rows.size(), scroll + SHOPS_PAGE); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            if (f.length < 10) continue;
            int y = t + 40 + (i - scroll) * 44;
            boolean open = Boolean.parseBoolean(f[5]);
            Theme.inset(g, l + 14, y, W - 28, 41);
            g.item(new ItemStack(iconFor(f[1])), l + 18, y + 4);
            Theme.text(g, f[1], l + 38, y + 5, Theme.INK);
            int mxw = W - 140 - this.font.width(f[1]);
            Theme.text(g, this.font.plainSubstrByWidth("makes " + f[9], mxw), l + 38 + this.font.width(f[1]) + 6, y + 5, Theme.INK_FAINT);
            String staff = f[2].equals("-") ? "No workers needed"
                    : !open ? "Closed" : f[3].isEmpty() ? "Hiring a " + f[2] + "..." : f[2] + ": " + f[3] + " (" + f[3].split(", ").length + "/" + f[4] + ")";
            Theme.text(g, this.font.plainSubstrByWidth(staff, W - 120), l + 38, y + 16, !open ? Theme.INK_FAINT : f[3].isEmpty() && !f[2].equals("-") ? Theme.GOLD : Theme.GREEN);
            String last = !f[7].isEmpty() ? f[7] : f[6].isEmpty() ? "Nothing made yet" : "Last: " + f[6];
            Theme.text(g, this.font.plainSubstrByWidth(last, W - 120), l + 38, y + 27, !f[7].isEmpty() ? Theme.RED : Theme.INK_SOFT);
        }
    }

    private static Item iconFor(String building) {
        return switch (building) {
            case "Mine" -> Items.IRON_PICKAXE;
            case "Lumber Mill" -> Items.OAK_PLANKS;
            case "Mill" -> ModContent.FLOUR.get();
            case "Bakery" -> Items.BREAD;
            case "Butcher" -> Items.COOKED_BEEF;
            case "Harbor" -> Items.COD;
            case "Tavern" -> Items.HONEY_BOTTLE;
            case "Chapel" -> Items.BELL;
            case "Archery Range" -> Items.BOW;
            default -> Items.CRAFTING_TABLE;
        };
    }

    // ------------------------------------------------------------------ defense

    private void drawDefense(GuiGraphicsExtractor g, int l, int t) {
        Theme.ribbon(g, "Defense", l + W / 2, t + 16);
        List<String[]> towers = new ArrayList<>(), threats = new ArrayList<>(), camps = new ArrayList<>();
        for (String r : info.defense()) {
            String[] f = r.split("\\|", -1);
            switch (f[0]) {
                case "tower" -> towers.add(f);
                case "threat" -> threats.add(f);
                case "camp" -> camps.add(f);
                default -> {}
            }
        }
        int x = l + 14, y = t + 36;
        Theme.heading(g, "Watchtowers", x, y, 200);
        y += 15;
        if (towers.isEmpty()) y = Theme.paragraph(g, "None yet. Build a Lookout Tower (Military): an archer keeps watch, the bell rings when enemies come near, "
                + "and you can claim land around it.", x, y, 200, Theme.INK_SOFT);
        for (String[] f : towers) {
            if (y > t + H - 50 || f.length < 9) break;
            Theme.inset(g, x, y - 3, 200, 32);
            Theme.icon(g, "tower", x + 4, y);
            Theme.text(g, f[1], x + 20, y + 1, Theme.INK);
            Theme.textRight(g, f[2] + ", " + f[3] + " " + "*".repeat(parse(f[4])), x + 196, y + 1, Theme.GOLD);
            String garrison = f[5].isEmpty() ? "No archers posted" : f[5];
            Theme.text(g, this.font.plainSubstrByWidth(garrison, 120) + " (" + (f[5].isEmpty() ? 0 : f[5].split(", ").length) + "/" + f[6] + ")",
                    x + 20, y + 11, f[5].isEmpty() ? Theme.RED : Theme.GREEN);
            Theme.text(g, "Sees " + f[7] + " blocks; land " + f[8] + " chunk" + (f[8].equals("1") ? "" : "s") + " around", x + 20, y + 20, Theme.INK_FAINT);
            y += 36;
        }
        int rx = l + 228;
        y = t + 36;
        Theme.heading(g, "Threats", rx, y, 198);
        y += 15;
        if (threats.isEmpty()) {
            Theme.icon(g, "check", rx, y - 2);
            Theme.text(g, "No one marches on you.", rx + 15, y, Theme.GREEN);
            y += 14;
        }
        for (String[] f : threats) {
            if (f.length < 6) continue;
            Theme.icon(g, "warn", rx, y - 2);
            String who = Character.toUpperCase(f[1].charAt(0)) + f[1].substring(1);
            Theme.text(g, this.font.plainSubstrByWidth(who, 180), rx + 15, y, Theme.RED);
            Theme.text(g, this.font.plainSubstrByWidth(f[4] + " strong, " + f[2] + " blocks " + f[3] + ", " + f[5], 182), rx + 15, y + 10, Theme.INK_SOFT);
            y += 24;
        }
        y += 6;
        Theme.heading(g, "Camps nearby", rx, y, 198);
        y += 15;
        if (camps.isEmpty()) Theme.text(g, "None known within 1200 blocks.", rx, y, Theme.INK_FAINT);
        for (String[] f : camps) {
            if (y > t + H - 40 || f.length < 5) break;
            boolean illager = f[1].startsWith("Illager");
            Theme.icon(g, illager ? "eye" : "swords", rx, y - 2);
            Theme.text(g, Character.toUpperCase(f[1].charAt(0)) + f[1].substring(1), rx + 15, y, illager ? Theme.PURPLE : 0xFF4A6A20);
            Theme.textRight(g, f[2] + " blocks " + f[3], rx + 198, y, Theme.INK_SOFT);
            y += 12;
        }
        Theme.text(g, "Sneak + right-click a tower's sign to raise it a level.", l + 14, t + H - 22, Theme.INK_FAINT);
    }

    // ------------------------------------------------------------------ diplomacy

    private void initDiplomacy(int l, int t) {
        List<String> rows = info.diplomacy();
        pager(l, t, rows.size(), 8);
        if (!info.ruler()) return;
        for (int i = scroll; i < Math.min(rows.size(), scroll + 8); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            String id = f[0], rel = f[3];
            boolean wild = Boolean.parseBoolean(f[6]);
            int y = t + 44 + (i - scroll) * 28;
            List<String[]> acts = new ArrayList<>();
            switch (rel) {
                case "WAR" -> acts.add(new String[]{"Sue for peace", "peace"});
                case "TRADING" -> {
                    acts.add(new String[]{"Ally", "alliance"});
                    acts.add(new String[]{"End", "break"});
                    acts.add(new String[]{"War", "war"});
                }
                case "ALLIED" -> acts.add(new String[]{"End alliance", "break"});
                default -> {
                    acts.add(new String[]{"War", "war"});
                    acts.add(new String[]{"Trade", "trade"});
                    if (wild) acts.add(new String[]{"Tribute", "tribute"});
                }
            }
            int x = l + W - 18;
            for (int a = acts.size() - 1; a >= 0; a--) {
                String[] act = acts.get(a);
                int w = this.font.width(act[0]) + 14;
                x -= w + 2;
                button(act[0], b -> send(Payloads.RealmAction.DIPLOMACY, act[1] + "|" + id), x, y + 3, w, 16);
            }
        }
    }

    private void drawDiplomacy(GuiGraphicsExtractor g, int l, int t) {
        Theme.ribbon(g, "Diplomacy", l + W / 2, t + 16);
        Theme.text(g, "Other kingdoms, nearest first. Wild kingdoms answer at once.", l + 16, t + 32, Theme.INK_SOFT);
        List<String> rows = info.diplomacy();
        if (rows.isEmpty()) Theme.text(g, "No other kingdoms known yet. Explore to find them.", l + 16, t + 50, Theme.INK);
        for (int i = scroll; i < Math.min(rows.size(), scroll + 8); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            int y = t + 44 + (i - scroll) * 28;
            Theme.inset(g, l + 14, y, W - 28, 25);
            int dye = Integer.parseInt(f[2]);
            g.fill(l + 18, y + 4, l + 22, y + 21, color(dye));
            Theme.text(g, this.font.plainSubstrByWidth(f[1], 130), l + 27, y + 4, inkOf(dye));
            String rel = switch (f[3]) {
                case "WAR" -> "At war";
                case "TRADING" -> "Trading";
                case "ALLIED" -> "Allied";
                default -> "At peace";
            };
            int relColor = switch (f[3]) {
                case "WAR" -> Theme.RED;
                case "TRADING" -> Theme.GOLD;
                case "ALLIED" -> Theme.GREEN;
                default -> Theme.INK_SOFT;
            };
            String dist = f[5].equals("-1") ? "far away" : f[5] + " blocks";
            String truce = !f[7].equals("0") ? ", truce " + f[7] + "d" : "";
            Theme.icon(g, f[3].equals("WAR") ? "swords" : f[3].equals("ALLIED") ? "check" : "crown", l + 164, y + 2);
            Theme.text(g, rel + truce, l + 180, y + 4, relColor);
            Theme.text(g, "~" + f[4] + " soldiers, " + dist, l + 27, y + 14, Theme.INK_FAINT);
        }
        if (!info.ruler()) Theme.text(g, "Only the ruler deals with other kingdoms.", l + 70, t + H - 22, Theme.INK_FAINT);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
