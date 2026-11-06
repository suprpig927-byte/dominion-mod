package com.dominionmod.dominion.block;

import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Sell your surplus for Gold Coins. Right-click with a stack to sell all of it. */
public class MarketBlock extends Block {
    /** How many of an item buy one Gold Coin. */
    private static final Map<Item, Integer> PER_COIN = new LinkedHashMap<>();
    private static final Map<TagKey<Item>, Integer> TAG_PER_COIN = new LinkedHashMap<>();

    static {
        PER_COIN.put(Items.WHEAT, 8);
        PER_COIN.put(Items.CARROT, 12);
        PER_COIN.put(Items.POTATO, 12);
        PER_COIN.put(Items.BEETROOT, 12);
        PER_COIN.put(Items.BREAD, 3);
        PER_COIN.put(Items.APPLE, 6);
        PER_COIN.put(Items.PUMPKIN, 4);
        PER_COIN.put(Items.MELON_SLICE, 24);
        PER_COIN.put(Items.SUGAR_CANE, 16);
        PER_COIN.put(Items.STICK, 64);
        PER_COIN.put(Items.COBBLESTONE, 64);
        PER_COIN.put(Items.COAL, 6);
        PER_COIN.put(Items.IRON_INGOT, 1);
        PER_COIN.put(Items.COPPER_INGOT, 4);
        PER_COIN.put(Items.LEATHER, 4);
        PER_COIN.put(Items.WHITE_WOOL, 8);
        PER_COIN.put(Items.EMERALD, 1);
        PER_COIN.put(Items.GOLD_INGOT, 1);
        TAG_PER_COIN.put(ItemTags.LOGS, 8);
        TAG_PER_COIN.put(ItemTags.PLANKS, 32);
    }

    /** Emeralds and gold ingots are worth more than one coin each. */
    private static int bonus(Item item) {
        if (item == Items.EMERALD) return 2;
        if (item == Items.GOLD_INGOT) return 3;
        return 1;
    }

    public MarketBlock(Properties props) {
        super(props);
    }

    public static int perCoin(ItemStack s) {
        Integer n = PER_COIN.get(s.getItem());
        if (n != null) return n;
        for (var e : TAG_PER_COIN.entrySet()) if (s.is(e.getKey())) return e.getValue();
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int per = perCoin(stack);
        if (per <= 0) {
            if (!level.isClientSide()) player.sendOverlayMessage(Realm.err("The market won't buy " + stack.getHoverName().getString() + "."));
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        int lots = stack.getCount() / per;
        if (lots == 0) {
            sp.sendOverlayMessage(Realm.err("You need at least " + per + " " + stack.getHoverName().getString() + " for one coin."));
            return InteractionResult.SUCCESS_SERVER;
        }
        int coins = lots * bonus(stack.getItem());
        String name = stack.getHoverName().getString();
        stack.shrink(lots * per);
        Realm.give(sp, new ItemStack(ModContent.GOLD_COIN.get(), coins));
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.8f, 1.0f);
        sp.sendOverlayMessage(Component.literal("Sold " + (lots * per) + " " + name + " for " + coins + " Gold Coin" + (coins == 1 ? "" : "s"))
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.sendSystemMessage(Component.literal("== Market: items per Gold Coin ==").withStyle(ChatFormatting.GOLD));
        StringBuilder sb = new StringBuilder();
        PER_COIN.forEach((item, n) -> {
            if (!sb.isEmpty()) sb.append(", ");
            int b = bonus(item);
            sb.append(new ItemStack(item).getHoverName().getString()).append(' ').append(b > 1 ? "1 = " + b + " coins" : String.valueOf(n));
        });
        sb.append(", any logs 8, any planks 32");
        player.sendSystemMessage(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Right-click with a stack to sell all of it.").withStyle(ChatFormatting.DARK_GRAY));
        return InteractionResult.SUCCESS_SERVER;
    }
}
