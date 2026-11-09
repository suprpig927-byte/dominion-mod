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
    BUILDER("Builder", 1, false),
    SCHOLAR("Scholar", 1, false),
    BLACKSMITH("Blacksmith", 1, false),
    // University classes (not from a tool: trained at the Training Desk)
    WIZARD("Wizard", -2, true),
    BARBARIAN("Barbarian", -2, true),
    MONK("Monk", -2, true),
    ENGINEER("Engineer", -1, false),
    /** Carries a Flintlock or Musket. */
    MUSKETEER("Musketeer", -2, true),
    // Trades: hired by a workshop (any citizen with empty hands can be taken on)
    MINER("Miner", 1, false),
    SAWYER("Sawyer", 1, false),
    MILLER("Miller", 1, false),
    BAKER("Baker", 1, false),
    BUTCHER("Butcher", 1, false),
    FISHER("Fisher", 1, false),
    INNKEEPER("Innkeeper", 1, false),
    PRIEST("Priest", 1, false);

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
        return this == FARMER || this == LUMBERJACK || this == BUILDER || this == ENGINEER || this == BLACKSMITH || this == SCHOLAR || trade();
    }

    /** Works at a workshop it was hired by (Mine, Mill, Bakery, Tavern...). */
    public boolean trade() {
        return ordinal() >= MINER.ordinal();
    }

    /** Trained at the University rather than given by a tool. */
    public boolean isClass() {
        return this == WIZARD || this == BARBARIAN || this == MONK || this == ENGINEER;
    }

    /** Builds construction sites (Engineers build twice as fast, and repair). */
    public boolean builds() {
        return this == BUILDER || this == ENGINEER;
    }

    /** Fights in melee (with a weapon or bare hands). */
    public boolean melee() {
        return this == MILITIA || this == BARBARIAN || this == MONK;
    }

    public static Job of(ItemStack held) {
        if (held.isEmpty()) return UNASSIGNED;
        if (held.is(com.dominionmod.dominion.registry.ModContent.BUILDERS_HAMMER.get())) return BUILDER;
        if (held.is(com.dominionmod.dominion.registry.ModContent.BLACKSMITH_TONGS.get())) return BLACKSMITH;
        if (held.is(net.minecraft.world.item.Items.BOOK) || held.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) return SCHOLAR;
        if (held.is(ItemTags.HOES)) return FARMER;
        if (held.is(ItemTags.AXES)) return LUMBERJACK;
        if (held.is(ItemTags.SWORDS) || held.is(ItemTags.SPEARS)) return MILITIA;
        if (held.getItem() instanceof BowItem) return ARCHER;
        if (held.getItem() instanceof com.dominionmod.dominion.item.FirearmItem) return MUSKETEER;
        return UNASSIGNED;
    }

    public static Job byId(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
