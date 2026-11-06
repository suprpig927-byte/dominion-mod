package com.dominionmod.dominion.client;

import com.dominionmod.dominion.kingdom.Scholarship;
import com.dominionmod.dominion.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Pick a University class for the citizens standing at the Training Desk. */
public class TrainingScreen extends Screen {
    private static final int W = 340, H = 200;
    private final Payloads.OpenTraining data;

    public TrainingScreen(Payloads.OpenTraining data) {
        super(Component.literal("University"));
        this.data = data;
    }

    public static void open(Payloads.OpenTraining data) {
        Minecraft.getInstance().gui.setScreen(new TrainingScreen(data));
    }

    private int left() { return (this.width - W) / 2; }
    private int top() { return (this.height - H) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        int i = 0;
        for (Scholarship.Course c : Scholarship.COURSES) {
            String arg = c.job().name() + "@" + data.desk().getX() + "," + data.desk().getY() + "," + data.desk().getZ();
            addRenderableWidget(Button.builder(Component.literal("Train"), b -> {
                ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.TRAIN, arg));
                onClose();
            }).bounds(l + W - 64, t + 44 + i * 34, 54, 18).build());
            i++;
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(l + W - 70, t + H - 26, 60, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, 0xE0141210);
        g.fill(l, t, l + W, t + 2, 0xFF8E6BD8);
        g.text(this.font, "THE UNIVERSITY", l + 10, t + 10, 0xFFD9C2FF, true);
        String who = String.join(", ", data.names());
        if (who.length() > 52) who = who.substring(0, 50) + "...";
        g.text(this.font, "Train: " + who, l + 10, t + 24, 0xFFCCCCCC, false);
        int i = 0;
        for (Scholarship.Course c : Scholarship.COURSES) {
            int y = t + 44 + i * 34;
            g.fill(l + 8, y - 2, l + W - 8, y + 28, 0x30FFFFFF);
            g.text(this.font, c.job().title, l + 12, y, 0xFFFFD86B, false);
            g.text(this.font, c.coins() + " coins" + (c.lapis() > 0 ? ", " + c.lapis() + " lapis" : "") + " each", l + 100, y, 0xFFBBBBBB, false);
            g.text(this.font, c.about(), l + 12, y + 12, 0xFF999999, false);
            i++;
        }
        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }
}
