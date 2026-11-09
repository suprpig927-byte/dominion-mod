package com.dominionmod.dominion.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** A wooden plaque button (or, as a tab, a wood tab that turns to parchment when selected). */
public class WoodButton extends Button {
    public enum Style { PLANK, TAB }

    private final Style style;
    private boolean selected;

    protected WoodButton(Builder b, Style style) {
        super(b);
        this.style = style;
    }

    public static WoodButton of(String label, OnPress press, int x, int y, int w, int h) {
        return of(label, press, x, y, w, h, null);
    }

    public static WoodButton of(String label, OnPress press, int x, int y, int w, int h, @Nullable String tip) {
        Builder b = Button.builder(Component.literal(label), press).bounds(x, y, w, h);
        if (tip != null) b.tooltip(Tooltip.create(Component.literal(tip)));
        return (WoodButton) b.build(bb -> new WoodButton(bb, Style.PLANK));
    }

    public static WoodButton tab(String label, OnPress press, int x, int y, int w, int h, boolean selected) {
        WoodButton t = (WoodButton) Button.builder(Component.literal(label), press).bounds(x, y, w, h).build(bb -> new WoodButton(bb, Style.TAB));
        t.selected = selected;
        return t;
    }

    /** For {@code Button.builder(...).build(WoodButton::plank)}. */
    public static WoodButton plank(Builder b) {
        return new WoodButton(b, Style.PLANK);
    }

    public WoodButton selected(boolean s) {
        this.selected = s;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        boolean hot = isHoveredOrFocused() && active;
        String sprite;
        int color;
        if (style == Style.TAB) {
            sprite = selected ? "ui/tab_active" : "ui/tab";
            color = selected ? Theme.INK : hot ? 0xFFFFE9A8 : 0xFFE8D6B0;
        } else if (selected) {
            sprite = "ui/inset_hot";
            color = Theme.INK;
        } else {
            sprite = !active ? "ui/button_disabled" : hot ? "ui/button_hover" : "ui/button";
            color = !active ? 0xFFB0A898 : hot ? 0xFFFFF0C0 : 0xFFF4E6C8;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, com.dominionmod.dominion.DominionMod.id(sprite), getX(), getY(), getWidth(), getHeight());
        var font = Theme.font();
        String text = getMessage().getString();
        if (font.width(text) > getWidth() - 6) text = font.plainSubstrByWidth(text, getWidth() - 10) + "..";
        boolean shadow = style == Style.PLANK && !selected;
        g.text(font, text, getX() + (getWidth() - font.width(text)) / 2, getY() + (getHeight() - 8) / 2, color, shadow);
    }
}
