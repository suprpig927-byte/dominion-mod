package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a labeled storehouse chest is for. Unlabeled chests take anything. */
public enum StorageCategory {
    ANY("Anything"),
    FOOD("Food"),
    FARMING("Farming"),
    WOOD("Wood"),
    MINING("Mining"),
    BUILDING("Building"),
    ARMORY("Armory"),
    COINS("Coins & Research");

    public final String title;

    StorageCategory(String title) {
        this.title = title;
    }

    public StorageCategory next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static StorageCategory byName(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ANY;
        }
    }

    /** The category an item belongs in. */
    public static StorageCategory of(ItemStack s) {
        if (s.is(ModContent.GOLD_COIN.get()) || s.is(Items.LAPIS_LAZULI) || s.is(Items.EMERALD) || s.is(Items.BOOK)) return COINS;
        if (s.has(DataComponents.FOOD)) return FOOD;
        if (s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || s.is(ItemTags.SAPLINGS) || s.is(Items.STICK)) return WOOD;
        if (s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.MELON_SEEDS) || s.is(Items.PUMPKIN_SEEDS)
                || s.is(Items.BONE_MEAL) || s.is(Items.HAY_BLOCK) || s.is(Items.SUGAR_CANE) || s.is(Items.LEATHER) || s.is(ItemTags.WOOL)
                || s.is(Items.EGG) || s.is(Items.FEATHER)) return FARMING;
        if (s.has(DataComponents.EQUIPPABLE) || s.is(ItemTags.SWORDS) || s.getItem() instanceof BowItem || s.is(Items.ARROW) || s.is(Items.SHIELD)
                || s.is(ItemTags.SPEARS)) return ARMORY;
        if (s.is(ItemTags.COALS) || s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER) || s.is(Items.IRON_INGOT)
                || s.is(Items.GOLD_INGOT) || s.is(Items.COPPER_INGOT) || s.is(Items.DIAMOND) || s.is(Items.REDSTONE) || s.is(Items.FLINT)
                || s.is(Items.IRON_NUGGET) || s.is(Items.GOLD_NUGGET) || s.is(Items.QUARTZ) || s.is(Items.AMETHYST_SHARD)) return MINING;
        if (s.getItem() instanceof BlockItem) return BUILDING;
        return ANY;
    }
}
