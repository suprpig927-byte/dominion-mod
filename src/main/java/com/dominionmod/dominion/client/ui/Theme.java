package com.dominionmod.dominion.client.ui;

import com.dominionmod.dominion.DominionMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The Dominion look: parchment in a dark wood frame with brass corners, wooden buttons, cloth title
 * ribbons, progress bars and little icons. Text is dark ink on parchment.
 */
public final class Theme {
    private Theme() {}

    public static final int INK = 0xFF2E1E10, INK_SOFT = 0xFF6B5236, INK_FAINT = 0xFF9A8262;
    public static final int GOLD = 0xFF9A6A08, RED = 0xFFA4271E, GREEN = 0xFF2F6E22, BLUE = 0xFF2C4D8E, PURPLE = 0xFF5E2A86;
    public static final int CREAM = 0xFFFFF4D6;

    private static Identifier ui(String name) {
        return DominionMod.id("ui/" + name);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000); // drop shadow
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("panel"), x, y, w, h);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("inset"), x, y, w, h);
    }

    public static void insetHot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("inset_hot"), x, y, w, h);
    }

    public static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui(name), x, y, w, h);
    }

    /** A 12x12 icon: crown, people, land, bell, warn, lock, check, eye, tower, swords, hammer, scroll. */
    public static void icon(GuiGraphicsExtractor g, String name, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("icon_" + name), x, y, 12, 12);
    }

    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
    }

    /** A cloth ribbon with a title on it, centered on {@code cx}. */
    public static void ribbon(GuiGraphicsExtractor g, String title, int cx, int y) {
        Font f = font();
        int w = f.width(title) + 34;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("ribbon"), cx - w / 2, y, w, 16);
        g.centeredText(f, title, cx, y + 4, CREAM);
    }

    /** A heading in ink with a thin rule under it. */
    public static void heading(GuiGraphicsExtractor g, String text, int x, int y, int w) {
        g.text(font(), text, x, y, INK, false);
        g.fill(x, y + 10, x + w, y + 11, 0x40553311);
    }

    public static void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(font(), text, x, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, String text, int right, int y, int color) {
        g.text(font(), text, right - font().width(text), y, color, false);
    }

    /** A progress bar, {@code pct} 0-100, in gold, green, red or blue. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int pct, String color) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("bar_empty"), x, y, w, 6);
        int fill = Math.max(0, Math.min(w - 2, (w - 2) * pct / 100));
        if (fill > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, ui("bar_" + color), x + 1, y + 1, fill, 4);
    }

    public static List<String> wrap(String text, int width) {
        Font f = font();
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String next = line.isEmpty() ? w : line + " " + w;
            if (f.width(next) > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    /** Wrapped text; returns the y below it. */
    public static int paragraph(GuiGraphicsExtractor g, String text, int x, int y, int width, int color) {
        for (String line : wrap(text, width)) {
            g.text(font(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    public static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void tooltip(GuiGraphicsExtractor g, List<String> lines, int mx, int my) {
        List<Component> cs = new ArrayList<>();
        for (String l : lines) cs.add(Component.literal(l));
        g.setTooltipForNextFrame(font(), cs, java.util.Optional.empty(), mx, my);
    }

    /** Dims the world behind a screen. */
    public static void backdrop(GuiGraphicsExtractor g, int w, int h) {
        g.fillGradient(0, 0, w, h, 0x90100804, 0xB0100804);
    }
}
