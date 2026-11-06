package com.dominionmod.dominion.entity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** A citizen's job comes from the tool in their hand: hoe = Farmer, axe = Lumberjack, sword or spear = Militia, bow = Archer. */
public enum Job {
    UNASSIGNED("Citizen", 0, false),
    FARMER("Farmer", 1, false),
    LUMBERJACK("Lumberjack", 1, false),
    MILITIA("Militia", -1, true),
    ARCHER("Archer", -1, true);

    public final String title;
    /** Coins paid in daily: positive = tax the citizen pays, negative = wage they're owed. */
    public final int dailyCoins;
    public final boolean soldier;

    Job(String title, int dailyCoins, boolean soldier) {
        this.title = title;
        this.dailyCoins = dailyCoins;
        this.soldier = soldier;
    }

    public boolean worker() {
        return this == FARMER || this == LUMBERJACK;
    }

    public static Job of(ItemStack held) {
        if (held.isEmpty()) return UNASSIGNED;
        if (held.is(ItemTags.HOES)) return FARMER;
        if (held.is(ItemTags.AXES)) return LUMBERJACK;
        if (held.is(ItemTags.SWORDS) || held.is(ItemTags.SPEARS)) return MILITIA;
        if (held.getItem() instanceof BowItem) return ARCHER;
        return UNASSIGNED;
    }

    public static Job byId(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
