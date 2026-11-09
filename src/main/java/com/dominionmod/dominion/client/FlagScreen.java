package com.dominionmod.dominion.client;

import com.dominionmod.dominion.kingdom.Flags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * The flag designer: pick a base color, then stack up to 8 pattern layers, each in its own color.
 * Used when founding a kingdom and from the Realm screen afterwards.
 */
public class FlagScreen extends Screen {
    private static final int W = 330, H = 236, SW = 12;
    private final Screen parent;
    private final Consumer<String> onDone;
    private final String doneLabel;
    private int base;
    private final List<Flags.Layer> layers;
    private int selected;
    private List<Holder.Reference<BannerPattern>> patterns = List.of();
    private BannerFlagModel flagModel;
    private final List<int[]> swatches = new ArrayList<>(); // x, y, row (0 = base, 1 = layer), dye

    public FlagScreen(Screen parent, String flag, Consumer<String> onDone, String doneLabel) {
        super(Component.literal("Design your flag"));
        this.parent = parent;
        this.onDone = onDone;
        this.doneLabel = doneLabel;
        this.base = Flags.base(flag);
        this.layers = new ArrayList<>(Flags.layers(flag));
        this.selected = layers.isEmpty() ? -1 : 0;
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return (this.height - H) / 2; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        flagModel = new BannerFlagModel(mc.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        if (mc.level != null) {
            patterns = mc.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements()
                    .sorted(Comparator.comparing(h -> h.key().identifier().toString())).toList();
        }
        int l = left(), t = top();
        int x = l + 80;
        swatches.clear();
        for (int i = 0; i < 16; i++) {
            final int dye = i;
            int sx = x + i * (SW + 2), sy = t + 30;
            addRenderableWidget(Button.builder(Component.empty(), b -> base = dye).bounds(sx, sy, SW, SW).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            swatches.add(new int[]{sx, sy, 0, dye});
        }
        // Layer list
        for (int i = 0; i < Flags.MAX_LAYERS; i++) {
            final int idx = i;
            int row = t + 58 + i * 14;
            if (i < layers.size()) {
                addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + patternName(layers.get(i))), b -> {
                    selected = idx;
                    rebuildWidgets();
                }).bounds(x, row, 150, 13).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            }
        }
        int ly = t + 58 + Flags.MAX_LAYERS * 14 + 4;
        if (selected >= 0 && selected < layers.size()) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).bounds(x + 156, t + 58 + selected * 14, 14, 13).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).bounds(x + 172, t + 58 + selected * 14, 14, 13).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            addRenderableWidget(Button.builder(Component.literal("Up"), b -> move(-1)).bounds(x + 188, t + 58 + selected * 14, 26, 13).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            addRenderableWidget(Button.builder(Component.literal("Dn"), b -> move(1)).bounds(x + 216, t + 58 + selected * 14, 26, 13).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
            for (int i = 0; i < 16; i++) {
                final int dye = i;
                int sx = x + i * (SW + 2), sy = ly + 12;
                addRenderableWidget(Button.builder(Component.empty(), b -> {
                    if (selected >= 0 && selected < layers.size()) layers.set(selected, new Flags.Layer(layers.get(selected).pattern(), dye));
                }).bounds(sx, sy, SW, SW).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
                swatches.add(new int[]{sx, sy, 1, dye});
            }
        }
        int by = t + H - 26;
        addRenderableWidget(Button.builder(Component.literal("+ Layer"), b -> {
            if (layers.size() >= Flags.MAX_LAYERS || patterns.isEmpty()) return;
            layers.add(new Flags.Layer(patterns.get(0).key().identifier().toString(), base == 15 ? 0 : 15));
            selected = layers.size() - 1;
            rebuildWidgets();
        }).bounds(l + 10, by, 54, 20).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
        addRenderableWidget(Button.builder(Component.literal("- Layer"), b -> {
            if (selected < 0 || selected >= layers.size()) return;
            layers.remove(selected);
            selected = Math.min(selected, layers.size() - 1);
            rebuildWidgets();
        }).bounds(l + 66, by, 54, 20).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
        addRenderableWidget(Button.builder(Component.literal("Copy held banner"), b -> copyHeld()).bounds(l + 122, by, 96, 20).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
        addRenderableWidget(Button.builder(Component.literal(doneLabel), b -> onDone.accept(Flags.encode(base, layers)))
                .bounds(l + W - 106, by, 96, 20).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(l + 10, t + 6, 40, 16).build(com.dominionmod.dominion.client.ui.WoodButton::plank));
    }

    private void cycle(int dir) {
        if (selected < 0 || selected >= layers.size() || patterns.isEmpty()) return;
        Flags.Layer cur = layers.get(selected);
        int idx = 0;
        for (int i = 0; i < patterns.size(); i++) if (patterns.get(i).key().identifier().toString().equals(cur.pattern())) idx = i;
        idx = Math.floorMod(idx + dir, patterns.size());
        layers.set(selected, new Flags.Layer(patterns.get(idx).key().identifier().toString(), cur.dye()));
        rebuildWidgets();
    }

    private void move(int dir) {
        int to = selected + dir;
        if (selected < 0 || to < 0 || to >= layers.size()) return;
        Flags.Layer l = layers.remove(selected);
        layers.add(to, l);
        selected = to;
        rebuildWidgets();
    }

    private void copyHeld() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (s.getItem() instanceof BannerItem) {
                String f = Flags.fromBanner(s);
                base = Flags.base(f);
                layers.clear();
                layers.addAll(Flags.layers(f));
                selected = layers.isEmpty() ? -1 : 0;
                rebuildWidgets();
                return;
            }
        }
    }

    private String patternName(Flags.Layer l) {
        for (var h : patterns) {
            if (h.key().identifier().toString().equals(l.pattern())) {
                return Component.translatable(h.value().translationKey() + "." + DyeColor.byId(l.dye()).getName()).getString();
            }
        }
        Identifier id = Identifier.tryParse(l.pattern());
        return id == null ? l.pattern() : id.getPath();
    }

    private BannerPatternLayers preview() {
        List<BannerPatternLayers.Layer> out = new ArrayList<>();
        for (Flags.Layer l : layers) {
            for (var h : patterns) {
                if (h.key().identifier().toString().equals(l.pattern())) out.add(new BannerPatternLayers.Layer(h, DyeColor.byId(l.dye())));
            }
        }
        return new BannerPatternLayers(out);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        com.dominionmod.dominion.client.ui.Theme.backdrop(g, this.width, this.height);
        com.dominionmod.dominion.client.ui.Theme.panel(g, l - 6, t - 6, W + 12, H + 12);
        g.text(this.font, "DESIGN YOUR FLAG", l + 60, t + 10, com.dominionmod.dominion.client.ui.Theme.GOLD, false);
        g.text(this.font, "Base color", l + 80, t + 20, com.dominionmod.dominion.client.ui.Theme.INK_SOFT, false);
        g.text(this.font, layers.isEmpty() ? "No patterns yet: press + Layer" : "Layers (bottom to top)", l + 80, t + 47, com.dominionmod.dominion.client.ui.Theme.INK_SOFT, false);
        if (selected >= 0 && selected < layers.size()) {
            g.text(this.font, "Color of layer " + (selected + 1), l + 80, t + 58 + Flags.MAX_LAYERS * 14 + 6, com.dominionmod.dominion.client.ui.Theme.INK_SOFT, false);
        }

        // Preview, drawn on a backing so it reads at a glance.
        g.fill(l + 8, t + 30, l + 72, t + 162, 0xFF2A2622);
        g.fill(l + 10, t + 34, l + 70, t + 36, 0xFF6B4A2B); // the pole
        var tex = com.dominionmod.dominion.client.render.FlagTextures.preview(DyeColor.byId(base), preview());
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, l + 10, t + 38, 0, 0, 60, 120, 20, 40, 20, 40);
        g.text(this.font, "Preview", l + 22, t + 164, com.dominionmod.dominion.client.ui.Theme.INK_FAINT, false);

        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));

        for (int[] sw : swatches) {
            int c = 0xFF000000 | DyeColor.byId(sw[3]).getTextureDiffuseColor();
            boolean picked = sw[2] == 0 ? sw[3] == base : selected >= 0 && selected < layers.size() && layers.get(selected).dye() == sw[3];
            if (picked) g.fill(sw[0] - 1, sw[1] - 1, sw[0] + SW + 1, sw[1] + SW + 1, 0xFFFFFFFF);
            g.fill(sw[0] + 1, sw[1] + 1, sw[0] + SW - 1, sw[1] + SW - 1, c);
        }
        if (selected >= 0 && selected < layers.size()) {
            int y = t + 58 + selected * 14;
            g.fill(l + 76, y, l + 78, y + 13, com.dominionmod.dominion.client.ui.Theme.GOLD);
        }
    }
}
