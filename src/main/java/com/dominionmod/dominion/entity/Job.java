package com.dominionmod.dominion.entity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** A citizen's job comes from the tool in their hand: hoe = Farmer, axe = Lumberjack, Builder's Hammer = Builder, sword or spear = Militia, bow = Archer. */
public enum Job {
    UNASSIGNED("Citizen", 0, false),
    FARMER("Farmer", 1, false),
    LUMBERJACK("Lumberjack", 1, false),
    MILITIA("Militia", -1, true),
    ARCHER("Archer", -1, true),
    BUILDER("Builder", 1, false);

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
        return this == FARMER || this == LUMBERJACK || this == BUILDER;
    }

    public static Job of(ItemStack held) {
        if (held.isEmpty()) return UNASSIGNED;
        if (held.is(com.dominionmod.dominion.registry.ModContent.BUILDERS_HAMMER.get())) return BUILDER;
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
