package com.dominionmod.dominion.client;

import com.dominionmod.dominion.client.ui.Theme;
import com.dominionmod.dominion.client.ui.WoodButton;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Market: today's prices (they follow what your storehouse already holds), what you carry and
 * what's in store, a Sell button for each good, and the caravans and their hired guards.
 */
public class MarketScreen extends Screen {
    private static final int W = 440, H = 280, COLS = 2, ROWS = 7;
    private static int page;
    private final Payloads.OpenMarket data;

    public MarketScreen(Payloads.OpenMarket data) {
        super(Component.literal("Market"));
        this.data = data;
    }

    public static void open(Payloads.OpenMarket data) {
        Minecraft.getInstance().gui.setScreen(new MarketScreen(data));
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return (this.height - H) / 2; }

    private String at() {
        return data.pos().getX() + "," + data.pos().getY() + "," + data.pos().getZ();
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        List<String> rows = data.rows();
        int per = COLS * ROWS;
        if (page * per >= rows.size()) page = 0;
        for (int i = page * per; i < Math.min(rows.size(), (page + 1) * per); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            int slot = i - page * per;
            int x = l + 14 + (slot % COLS) * 206, y = t + 40 + (slot / COLS) * 24;
            int per1 = parse(f[2]);
            WoodButton sell = addRenderableWidget(WoodButton.of("Sell", b -> ClientPacketDistributor.sendToServer(
                    new Payloads.RealmAction(Payloads.RealmAction.MARKET_SELL, f[0] + "@" + at())), x + 160, y + 3, 38, 15));
            sell.active = parse(f[4]) >= Math.max(1, per1);
        }
        if (rows.size() > per) {
            addRenderableWidget(WoodButton.of("<", b -> { page = Math.max(0, page - 1); rebuildWidgets(); }, l + 14, t + H - 28, 22, 18)).active = page > 0;
            addRenderableWidget(WoodButton.of(">", b -> { page++; rebuildWidgets(); }, l + 38, t + H - 28, 22, 18)).active = (page + 1) * per < rows.size();
        }
        if (data.owner()) {
            int g = data.guards();
            addRenderableWidget(WoodButton.of("-", b -> ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.MARKET_GUARDS,
                    Math.max(0, g - 1) + "@" + at())), l + 150, t + H - 52, 18, 15)).active = g > 0;
            addRenderableWidget(WoodButton.of("+", b -> ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.MARKET_GUARDS,
                    Math.min(6, g + 1) + "@" + at())), l + 196, t + H - 52, 18, 15)).active = g < 6;
        }
        addRenderableWidget(WoodButton.of("Close", b -> onClose(), l + W - 66, t + H - 28, 54, 18));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack stackOf(String key) {
        if (key.startsWith("#")) {
            return new ItemStack(switch (key) {
                case "#logs" -> Items.OAK_LOG;
                case "#planks" -> Items.OAK_PLANKS;
                default -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
            });
        }
        Identifier id = Identifier.tryParse(key);
        Item item = id == null ? Items.BARRIER : BuiltInRegistries.ITEM.getValue(id);
        return new ItemStack(item);
    }

    private static String nameOf(String key, ItemStack stack) {
        return switch (key) {
            case "#logs" -> "Any logs";
            case "#planks" -> "Any planks";
            case "#wool" -> "Any wool";
            default -> stack.getHoverName().getString();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        Theme.backdrop(g, this.width, this.height);
        Theme.panel(g, l, t, W, H);
        Theme.ribbon(g, "Market", l + W / 2, t + 12);
        g.item(new ItemStack(ModContent.GOLD_COIN.get()), l + W - 80, t + 10);
        Theme.text(g, data.coins() + " on you", l + W - 62, t + 14, Theme.GOLD);
        Theme.text(g, "Items per Gold Coin today. Plenty in store: cheaper. Scarce: dearer.", l + 16, t + 28, Theme.INK_SOFT);
        List<String> rows = data.rows();
        int per = COLS * ROWS;
        for (int i = page * per; i < Math.min(rows.size(), (page + 1) * per); i++) {
            String[] f = rows.get(i).split("\\|", -1);
            if (f.length < 6) continue;
            int slot = i - page * per;
            int x = l + 14 + (slot % COLS) * 206, y = t + 40 + (slot / COLS) * 24;
            Theme.inset(g, x, y, 202, 21);
            ItemStack stack = stackOf(f[0]);
            g.item(stack, x + 3, y + 2);
            String name = nameOf(f[0], stack);
            Theme.text(g, this.font.plainSubstrByWidth(name, 70), x + 22, y + 3, Theme.INK);
            int base = parse(f[1]), now = parse(f[2]), bonus = parse(f[3]);
            String price = bonus > 1 ? "1 = " + bonus + " coins" : now + " / coin";
            int col = now > base ? Theme.GREEN : now < base ? Theme.RED : Theme.INK_SOFT;
            String trend = now > base ? " glut" : now < base ? " scarce" : "";
            Theme.text(g, price + trend, x + 22, y + 12, bonus > 1 ? Theme.GOLD : col);
            Theme.textRight(g, "you " + f[4], x + 156, y + 3, Theme.INK_SOFT);
            Theme.textRight(g, "store " + f[5], x + 156, y + 12, Theme.INK_FAINT);
            if (Theme.over(mouseX, mouseY, x, y, 158, 21)) Theme.tooltip(g, List.of(name, "Base price: " + base + " a coin", "Today: " + now + " a coin"
                    + (now > base ? " (your stores are full of it)" : now < base ? " (scarce in your stores)" : ""),
                    "You carry " + f[4] + "; your storehouse holds " + f[5]), mouseX, mouseY);
        }
        int y = t + H - 56;
        if (data.owner()) {
            g.fill(l + 14, y - 6, l + W - 14, y - 5, 0x40553311);
            Theme.icon(g, "swords", l + 16, y);
            Theme.text(g, "Caravan guards:", l + 32, y + 3, Theme.INK);
            Theme.text(g, String.valueOf(data.guards()), l + 178, y + 3, Theme.INK);
            Theme.text(g, "2 coins each a trip", l + 222, y + 3, Theme.INK_FAINT);
            int cy = y + 18;
            if (data.caravans().isEmpty()) Theme.text(g, "No caravans on the road.", l + 70, cy + 4, Theme.INK_FAINT);
            else for (int i = 0; i < Math.min(2, data.caravans().size()); i++)
                Theme.text(g, this.font.plainSubstrByWidth(data.caravans().get(i), W - 150), l + 70, cy + i * 10, Theme.INK_SOFT);
        } else {
            Theme.text(g, "Sell here at any market. Only the land's own kingdom sends caravans from it.", l + 16, y + 3, Theme.INK_FAINT);
        }
        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
