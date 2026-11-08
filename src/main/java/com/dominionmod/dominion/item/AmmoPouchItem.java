package com.dominionmod.dominion.item;

import com.dominionmod.dominion.registry.ModContent;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A leather Ammo Pouch that holds up to 512 Bullets. Right-click to sweep the bullets in your
 * inventory into it; sneak + right-click to take a handful out. Flintlocks and Muskets load
 * straight from a pouch anywhere in your inventory.
 */
public class AmmoPouchItem extends Item {
    public static final int CAPACITY = 512;

    public AmmoPouchItem(Properties props) {
        super(props);
    }

    public static int count(ItemStack pouch) {
        CustomData d = pouch.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr("Bullets", 0);
    }

    public static void setCount(ItemStack pouch, int n) {
        CustomData.update(DataComponents.CUSTOM_DATA, pouch, t -> t.putInt("Bullets", Math.max(0, Math.min(CAPACITY, n))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack pouch = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        int n = count(pouch);
        Inventory inv = sp.getInventory();
        if (sp.isShiftKeyDown()) {
            int take = Math.min(64, n);
            if (take == 0) {
                sp.sendOverlayMessage(Component.literal("The pouch is empty.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            setCount(pouch, n - take);
            ItemStack out = new ItemStack(ModContent.BULLET.get(), take);
            if (!inv.add(out)) com.dominionmod.dominion.kingdom.Realm.give(sp, out);
            sp.sendOverlayMessage(Component.literal("Took " + take + " bullets out (" + (n - take) + " left).").withStyle(ChatFormatting.YELLOW));
        } else {
            int moved = 0;
            for (int i = 0; i < inv.getContainerSize() && n < CAPACITY; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(ModContent.BULLET.get())) continue;
                int m = Math.min(s.getCount(), CAPACITY - n);
                s.shrink(m);
                n += m;
                moved += m;
            }
            setCount(pouch, n);
            sp.sendOverlayMessage(Component.literal(moved == 0 ? "No loose bullets to put away (" + n + "/" + CAPACITY + ")."
                    : "Put " + moved + " bullets in the pouch (" + n + "/" + CAPACITY + ").").withStyle(ChatFormatting.YELLOW));
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return count(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(13, Mth.ceil(13.0f * count(stack) / CAPACITY));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8A060;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(count(stack) + " / " + CAPACITY + " bullets").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Right-click: put bullets in. Sneak: take some out.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
