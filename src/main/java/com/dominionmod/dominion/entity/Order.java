package com.dominionmod.dominion.entity;

/** What a citizen has been told to do with the Command Baton. */
public enum Order {
    /** No standing order: workers work, soldiers guard their post. */
    DUTY,
    /** Fall in behind the commander: a column on the move, a battle line when they stop. */
    RALLY,
    /** Stand at the anchor point and fight only what comes close. */
    HOLD,
    /** Walk a route of waypoints round and round, fighting whatever turns up. */
    PATROL,
    /** March across country to the anchor (an army on campaign), fighting enemies on the way. */
    MARCH;

    public static Order byId(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
