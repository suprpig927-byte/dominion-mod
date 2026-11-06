package com.dominionmod.dominion.kingdom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The tech tree researched at the Research Table. Costs come out of the storehouse. */
public final class Research {
    private Research() {}

    public record Tech(String id, String name, String unlocks, int coins, int lapis, double days, List<String> requires) {
        /** Work needed to finish, in ticks at the base rate (no Scholars). */
        public double work() {
            return days * 24000.0;
        }
    }

    private static final Map<String, Tech> ALL = new LinkedHashMap<>();

    private static void add(String id, String name, String unlocks, int coins, int lapis, double days, String... requires) {
        ALL.put(id, new Tech(id, name, unlocks, coins, lapis, days, List.of(requires)));
    }

    static {
        add("timber", "Carpentry", "Timber buildings: plank walls, log frames, glass windows. Barracks.", 15, 4, 0.25);
        add("stone", "Masonry", "Stone buildings, the Watchtower, Stone Walls and the Wall Gate.", 30, 10, 0.5, "timber");
        add("blacksmithing", "Blacksmithing", "The Blacksmith job (Blacksmith's Tongs) and the Smithy.", 25, 8, 0.5, "timber");
        add("brick", "Brickworks", "Brick buildings with dark oak trim and carpets.", 60, 20, 0.75, "stone");
        add("university", "Higher Learning", "The University: train Wizards, Barbarians, Monks and Engineers.", 80, 30, 1.0, "stone");
        add("royal", "Royal Architecture", "Royal buildings: deepslate walls and copper roofs.", 120, 40, 1.0, "brick");
    }

    public static java.util.Collection<Tech> all() {
        return ALL.values();
    }

    public static @Nullable Tech get(String id) {
        return ALL.get(id);
    }

    /** Extra work per tick from each Scholar at a Research Table (on top of the base 1). */
    public static final double PER_SCHOLAR = 2.0;
    public static final int MAX_SCHOLARS = 3;
}
