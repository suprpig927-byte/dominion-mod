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

/**
 * Sell your surplus for Gold Coins. Right-click with a stack to sell all of it. Prices follow
 * supply: whatever the kingdom's storehouses are already full of fetches less, and what's scarce
 * fetches more. Right-click with an empty hand for the price list and to hire caravan guards.
 */
public class MarketBlock extends Block {
    /** How many of an item buy one Gold Coin. */
    private static final Map<Item, Integer> PER_COIN = new LinkedHashMap<>();
    private static final Map<TagKey<Item>, Integer> TAG_PER_COIN = new LinkedHashMap<>();
    private static final int FLOUR_PER_COIN = 6;

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
        PER_COIN.put(Items.EMERALD, 1);
        PER_COIN.put(Items.GOLD_INGOT, 1);
        PER_COIN.put(Items.DIAMOND, 1);
        PER_COIN.put(Items.COOKED_BEEF, 3);
        PER_COIN.put(Items.COOKED_PORKCHOP, 3);
        PER_COIN.put(Items.COOKED_MUTTON, 3);
        PER_COIN.put(Items.COOKED_CHICKEN, 4);
        PER_COIN.put(Items.COD, 6);
        PER_COIN.put(Items.COOKED_COD, 4);
        PER_COIN.put(Items.SALMON, 5);
        PER_COIN.put(Items.RAW_IRON, 2);
        PER_COIN.put(Items.RAW_COPPER, 6);
        PER_COIN.put(Items.LAPIS_LAZULI, 4);
        PER_COIN.put(Items.REDSTONE, 8);
        PER_COIN.put(Items.FEATHER, 16);
        TAG_PER_COIN.put(ItemTags.LOGS, 8);
        TAG_PER_COIN.put(ItemTags.PLANKS, 32);
        TAG_PER_COIN.put(ItemTags.WOOL, 8);
    }

    /** Emeralds and gold ingots are worth more than one coin each. */
    private static int bonus(Item item) {
        if (item == Items.EMERALD) return 2;
        if (item == Items.GOLD_INGOT) return 3;
        if (item == Items.DIAMOND) return 10;
        return 1;
    }

    private static Component link(String text, String command) {
        return Component.literal(text).withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command)));
    }

    public MarketBlock(Properties props) {
        super(props);
    }

    public static int perCoin(ItemStack s) {
        if (s.is(ModContent.FLOUR.get())) return FLOUR_PER_COIN; // registered after this block, so not in the static table
        Integer n = PER_COIN.get(s.getItem());
        if (n != null) return n;
        for (var e : TAG_PER_COIN.entrySet()) if (s.is(e.getKey())) return e.getValue();
        return 0;
    }

    /** Items per coin at this market right now, given what the land's kingdom already has in store. */
    public static int priceAt(Level level, BlockPos pos, ItemStack s) {
        int base = perCoin(s);
        if (base <= 0 || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return base;
        var k = com.dominionmod.dominion.kingdom.KingdomData.get(sl.getServer()).ownerAt(level, pos);
        if (k == null) return base;
        int stock = com.dominionmod.dominion.kingdom.Storehouse.count(com.dominionmod.dominion.kingdom.Storehouse.containers(sl, k), x -> x.is(s.getItem()));
        double f = stock < base * 2 ? 0.75 : 1.0 + stock / (base * 48.0);
        return Math.max(1, (int) Math.round(base * Math.min(f, 4.0)));
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
        per = priceAt(level, pos, stack);
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
        if (player instanceof ServerPlayer sp) openFor(sp, pos);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    // ------------------------------------------------------------------ the Market screen

    private static final String[] TAG_KEYS = {"#logs", "#planks", "#wool"};

    private static java.util.function.Predicate<ItemStack> matcher(String key) {
        for (var e : TAG_PER_COIN.entrySet()) if (("#" + e.getKey().location().getPath()).equals(key)) return s -> s.is(e.getKey());
        var id = net.minecraft.resources.Identifier.tryParse(key);
        if (id == null) return s -> false;
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        return s -> s.is(item);
    }

    private static int tagPrice(String key) {
        for (var e : TAG_PER_COIN.entrySet()) if (("#" + e.getKey().location().getPath()).equals(key)) return e.getValue();
        return 0;
    }

    private static int carried(ServerPlayer p, java.util.function.Predicate<ItemStack> m) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && m.test(s)) n += s.getCount();
        return n;
    }

    public static void openFor(ServerPlayer p, BlockPos pos) {
        var level = (net.minecraft.server.level.ServerLevel) p.level();
        var d = com.dominionmod.dominion.kingdom.KingdomData.get(level.getServer());
        var owner = d.ownerAt(level, pos);
        var store = owner == null ? java.util.List.<net.minecraft.world.Container>of() : com.dominionmod.dominion.kingdom.Storehouse.containers(level, owner);
        java.util.List<String> rows = new java.util.ArrayList<>();
        java.util.Map<Item, Integer> goods = new LinkedHashMap<>(PER_COIN);
        goods.put(ModContent.FLOUR.get(), FLOUR_PER_COIN);
        goods.forEach((item, base) -> {
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            int stock = com.dominionmod.dominion.kingdom.Storehouse.count(store, s -> s.is(item));
            rows.add(key + "|" + base + "|" + priceAt(level, pos, new ItemStack(item)) + "|" + bonus(item) + "|" + carried(p, s -> s.is(item)) + "|" + stock);
        });
        for (String key : TAG_KEYS) {
            var m = matcher(key);
            rows.add(key + "|" + tagPrice(key) + "|" + tagPrice(key) + "|1|" + carried(p, m) + "|" + com.dominionmod.dominion.kingdom.Storehouse.count(store, m));
        }
        var k = d.byPlayer(p.getUUID());
        boolean mine = k != null && k == owner;
        java.util.List<String> caravans = new java.util.ArrayList<>();
        if (mine) for (String line : com.dominionmod.dominion.kingdom.Caravans.describe(level)) if (line.startsWith(k.name + " ")) caravans.add(line);
        int coins = carried(p, s -> s.is(ModContent.GOLD_COIN.get()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.dominionmod.dominion.network.Payloads.OpenMarket(pos, rows,
                mine ? d.progress(k).caravanGuards : -1, caravans, mine, coins));
    }

    /** "key@x,y,z": sell every one of that good the player carries. */
    public static void sellFromScreen(ServerPlayer p, String arg) {
        String[] f = arg.split("@", 2);
        if (f.length < 2) return;
        String[] c = f[1].split(",");
        BlockPos pos;
        try {
            pos = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
        } catch (RuntimeException e) {
            return;
        }
        var level = p.level();
        if (!(level.getBlockState(pos).getBlock() instanceof MarketBlock) || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 10 * 10) return;
        var m = matcher(f[0]);
        int sold = 0, coins = 0;
        String name = "";
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            if (s.isEmpty() || !m.test(s)) continue;
            int per = f[0].startsWith("#") ? tagPrice(f[0]) : priceAt(level, pos, s);
            if (per <= 0) continue;
            int lots = s.getCount() / per;
            if (lots == 0) continue;
            name = s.getHoverName().getString();
            sold += lots * per;
            coins += lots * bonus(s.getItem());
            s.shrink(lots * per);
        }
        if (coins > 0) {
            Realm.give(p, new ItemStack(ModContent.GOLD_COIN.get(), coins));
            level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.8f, 1.0f);
            p.sendOverlayMessage(Component.literal("Sold " + sold + " " + name + " for " + coins + " Gold Coin" + (coins == 1 ? "" : "s")).withStyle(ChatFormatting.GOLD));
        } else {
            p.sendOverlayMessage(Realm.err("You don't carry enough of that to make a coin."));
        }
        openFor(p, pos);
    }

    /** "n@x,y,z": set the caravan guards. */
    public static void guardsFromScreen(ServerPlayer p, String arg) {
        String[] f = arg.split("@", 2);
        if (f.length < 2) return;
        String[] c = f[1].split(",");
        try {
            int n = Math.max(0, Math.min(6, Integer.parseInt(f[0])));
            BlockPos pos = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            var level = (net.minecraft.server.level.ServerLevel) p.level();
            var d = com.dominionmod.dominion.kingdom.KingdomData.get(level.getServer());
            var k = d.byPlayer(p.getUUID());
            if (k != null && k == d.ownerAt(level, pos)) {
                d.progress(k).caravanGuards = n;
                d.markChanged();
            }
            openFor(p, pos);
        } catch (RuntimeException ignored) {
        }
    }
}
