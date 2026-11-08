package com.dominionmod.dominion.magic;

/** The spells in a Spell Book. Cost is in experience points (your mana); cooldown in ticks. */
public enum Spell {
    FIREBALL("Fireball", "Hurl a ball of fire.", 3, 20, 0xFF7A1E),
    FANGS("Evoker Fangs", "A line of fangs bursts from the ground toward your aim.", 4, 40, 0xB04CE0),
    FROST_NOVA("Frost Nova", "Freeze and slow every enemy around you.", 6, 100, 0x8CE6FF),
    LIGHTNING("Lightning", "Call lightning down where you look.", 8, 120, 0xFFF06A),
    HEAL("Healing Light", "Heal yourself and your people nearby.", 5, 120, 0x7CFF7C),
    BLINK("Blink", "Teleport a short way forward.", 4, 40, 0x6A4CFF);

    public final String title, description;
    public final int cost, cooldown, color;

    Spell(String title, String description, int cost, int cooldown, int color) {
        this.title = title;
        this.description = description;
        this.cost = cost;
        this.cooldown = cooldown;
        this.color = color;
    }

    public static Spell byId(int i) {
        Spell[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
