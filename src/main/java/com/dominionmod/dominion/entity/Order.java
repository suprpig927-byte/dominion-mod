package com.dominionmod.dominion.entity;

/** What a citizen has been told to do with the Command Baton. */
public enum Order {
    /** No standing order: workers work, soldiers guard their post. */
    DUTY,
    /** Fall in behind the commander: a column on the move, a battle line when they stop. */
    RALLY,
    /** Stand at the anchor point and fight only what comes close. */
    HOLD;

    public static Order byId(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
