package com.dominionmod.dominion.client;

import com.dominionmod.dominion.client.ui.Theme;
import com.dominionmod.dominion.client.ui.WoodButton;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.kingdom.Scholarship;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Pick a University class for the citizens standing at the Training Desk. */
public class TrainingScreen extends Screen {
    private static final int W = 360, H = 232;
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
            addRenderableWidget(WoodButton.of("Train", b -> {
                ClientPacketDistributor.sendToServer(new Payloads.RealmAction(Payloads.RealmAction.TRAIN, arg));
                onClose();
            }, l + W - 70, t + 56 + i * 38 + 10, 54, 16));
            i++;
        }
        addRenderableWidget(WoodButton.of("Cancel", b -> onClose(), l + W - 70, t + H - 28, 56, 18));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack iconOf(Job j) {
        return switch (j) {
            case WIZARD -> new ItemStack(ModContent.SPELL_BOOK.get());
            case BARBARIAN -> new ItemStack(Items.IRON_AXE);
            case MONK -> new ItemStack(Items.GOLDEN_APPLE);
            case ENGINEER -> new ItemStack(ModContent.BUILDERS_HAMMER.get());
            default -> new ItemStack(Items.BOOK);
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        Theme.backdrop(g, this.width, this.height);
        Theme.panel(g, l, t, W, H);
        Theme.ribbon(g, "The University", l + W / 2, t + 12);
        String who = String.join(", ", data.names());
        Theme.text(g, "Training: " + this.font.plainSubstrByWidth(who.isEmpty() ? "nobody at the desk" : who, W - 90), l + 16, t + 36, Theme.INK);
        int i = 0;
        for (Scholarship.Course c : Scholarship.COURSES) {
            int y = t + 56 + i * 38;
            Theme.inset(g, l + 12, y, W - 24, 35);
            g.item(iconOf(c.job()), l + 18, y + 9);
            Theme.text(g, c.job().title, l + 40, y + 5, Theme.PURPLE);
            g.item(new ItemStack(ModContent.GOLD_COIN.get()), l + 120, y + 1);
            Theme.text(g, String.valueOf(c.coins()), l + 137, y + 5, Theme.INK);
            if (c.lapis() > 0) {
                g.item(new ItemStack(Items.LAPIS_LAZULI), l + 158, y + 1);
                Theme.text(g, String.valueOf(c.lapis()), l + 175, y + 5, Theme.INK);
            }
            Theme.text(g, "each", l + 192, y + 5, Theme.INK_FAINT);
            Theme.text(g, this.font.plainSubstrByWidth(c.about(), W - 120), l + 40, y + 20, Theme.INK_SOFT);
            i++;
        }
        Theme.text(g, "Paid from the storehouse for each citizen at the desk.", l + 16, t + H - 22, Theme.INK_FAINT);
        this.renderables.forEach(r -> r.extractRenderState(g, mouseX, mouseY, partialTick));
    }
}
