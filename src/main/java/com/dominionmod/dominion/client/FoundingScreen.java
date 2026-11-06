package com.dominionmod.dominion.client;

import com.dominionmod.dominion.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Name your kingdom, pick its two colors, and raise your flag. */
public class FoundingScreen extends Screen {
    private static final int W = 300, H = 210, SW = 16;
    private final BlockPos hall;
    private EditBox name;
    private String typed = "";
    private int primary = DyeColor.RED.getId(), secondary = DyeColor.YELLOW.getId();
    private final List<int[]> swatches = new ArrayList<>(); // x, y, row, dye
    private String flag;

    public FoundingScreen(BlockPos hall) {
        super(Component.literal("Found a Kingdom"));
        this.hall = hall;
    }

    public static void open(BlockPos hall) {
        Minecraft.getInstance().gui.setScreen(new FoundingScreen(hall));
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return (this.height - H) / 2; }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 34;
        name = new EditBox(this.font, x, y, W - 24, 18, Component.literal("Kingdom name"));
        name.setMaxLength(24);
        name.setValue(typed);
        name.setResponder(s -> typed = s);
        addRenderableWidget(name);
        setInitialFocus(name);

        swatches.clear();
        for (int row = 0; row < 2; row++) {
            int sy = top() + 80 + row * 38;
            for (int i = 0; i < 16; i++) {
                int sx = x + i * (SW + 1);
                final int dye = i, r = row;
                addRenderableWidget(Button.builder(Component.empty(), b -> {
                    if (r == 0) primary = dye;
                    else secondary = dye;
                }).bounds(sx, sy, SW, SW).build());
                swatches.add(new int[]{sx, sy, row, dye});
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Next: Your Flag >"), b -> {
            if (flag == null) flag = com.dominionmod.dominion.kingdom.Flags.encode(primary,
                    List.of(new com.dominionmod.dominion.kingdom.Flags.Layer("minecraft:stripe_middle", secondary)));
            Minecraft.getInstance().gui.setScreen(new FlagScreen(this, flag, design -> {
                flag = design;
                ClientPacketDistributor.sendToServer(new Payloads.FoundKingdom(hall, typed, primary, secondary, design));
                Minecraft.getInstance().gui.setScreen(null);
            }, "Found Kingdom"));
        }).bounds(left() + W - 122, top() + H - 28, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Later"), b -> onClose())
                .bounds(left() + 12, top() + H - 28, 60, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        int p = 0xFF000000 | DyeColor.byId(primary).getTextureDiffuseColor();
        int s = 0xFF000000 | DyeColor.byId(secondary).getTextureDiffuseColor();
        g.fill(l, t, l + W, t + H, 0xE0141210);
        g.fill(l, t, l + W, t + 2, p);
        g.fill(l, t + H - 2, l + W, t + H, s);
        g.text(this.font, "FOUND A KINGDOM", l + 12, t + 10, 0xFFFFD86B, true);
        g.text(this.font, "Name", l + 12, t + 24, 0xFFBBBBBB, false);
        g.text(this.font, "Primary color: " + nice(primary), l + 12, t + 70, 0xFFBBBBBB, false);
        g.text(this.font, "Secondary color: " + nice(secondary), l + 12, t + 108, 0xFFBBBBBB, false);

        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));

        for (int[] sw : swatches) {
            int c = 0xFF000000 | DyeColor.byId(sw[3]).getTextureDiffuseColor();
            boolean picked = sw[2] == 0 ? sw[3] == primary : sw[3] == secondary;
            if (picked) g.fill(sw[0] - 1, sw[1] - 1, sw[0] + SW + 1, sw[1] + SW + 1, 0xFFFFFFFF);
            g.fill(sw[0] + 1, sw[1] + 1, sw[0] + SW - 1, sw[1] + SW - 1, c);
        }

        // Preview: a soldier's tabard in your colors.
        int px = l + 12, py = t + 140;
        g.fill(px, py, px + 22, py + 30, s);
        g.fill(px + 2, py + 2, px + 20, py + 28, p);
        g.fill(px + 9, py + 8, px + 13, py + 20, s);
        g.fill(px + 5, py + 12, px + 17, py + 16, s);
        g.text(this.font, "Your soldiers' tabards come in these colors,", px + 30, py + 4, 0xFFDDDDDD, false);
        g.text(this.font, "with your flag on the chest.", px + 30, py + 16, 0xFFDDDDDD, false);
        g.text(this.font, "Next you'll design the flag itself.", px + 30, py + 26, 0xFF777777, false);
    }

    private static String nice(int dye) {
        String n = DyeColor.byId(dye).getName().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
