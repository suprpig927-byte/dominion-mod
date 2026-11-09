package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.build.Palette;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/** A kingdom's research, finished buildings and storage labels. Saved in {@link KingdomData}. */
public final class Progress {
    public final UUID kingdom;
    public final Set<String> researched = new LinkedHashSet<>();
    public String current = "";
    public double work;
    public final List<Building> buildings = new ArrayList<>();
    /** Chest/barrel position (packed) -> storage category name. */
    public final Map<Long, String> labels = new HashMap<>();
    /** Research Tables placed on the kingdom's land (packed positions). */
    public final List<Long> tables = new ArrayList<>();
    /** Town Halls of towns this kingdom has conquered: its caravans trade with them. */
    public final List<Long> outposts = new ArrayList<>();
    /** Guards hired at the market to ride with each caravan (2 coins a trip each). */
    public int caravanGuards;

    /** Scholars seen working this second: citizen -> game time. Not saved. */
    public final Map<UUID, Long> scholars = new HashMap<>();

    public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(p -> p.kingdom),
            Codec.STRING.listOf().optionalFieldOf("researched", List.of()).forGetter(p -> new ArrayList<>(p.researched)),
            Codec.STRING.optionalFieldOf("current", "").forGetter(p -> p.current),
            Codec.DOUBLE.optionalFieldOf("work", 0.0).forGetter(p -> p.work),
            Building.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(p -> p.buildings),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("labels", Map.of()).forGetter(Progress::labelsAsStrings),
            Codec.LONG.listOf().optionalFieldOf("tables", List.of()).forGetter(p -> p.tables),
            Codec.LONG.listOf().optionalFieldOf("outposts", List.of()).forGetter(p -> p.outposts),
            Codec.INT.optionalFieldOf("caravan_guards", 0).forGetter(p -> p.caravanGuards)
    ).apply(i, Progress::new));

    private Progress(UUID kingdom, List<String> researched, String current, double work, List<Building> buildings, Map<String, String> labels,
                     List<Long> tables, List<Long> outposts, int caravanGuards) {
        this.outposts.addAll(outposts);
        this.caravanGuards = caravanGuards;
        this.kingdom = kingdom;
        this.researched.addAll(researched);
        this.current = current;
        this.work = work;
        this.buildings.addAll(buildings);
        labels.forEach((k, v) -> {
            try {
                this.labels.put(Long.parseLong(k), v);
            } catch (NumberFormatException ignored) {
            }
        });
        this.tables.addAll(tables);
    }

    public Progress(UUID kingdom) {
        this.kingdom = kingdom;
    }

    private Map<String, String> labelsAsStrings() {
        Map<String, String> out = new HashMap<>();
        labels.forEach((k, v) -> out.put(Long.toString(k), v));
        return out;
    }

    public boolean has(@Nullable String research) {
        return research == null || research.isEmpty() || researched.contains(research);
    }

    /** The best building tier this kingdom has researched. */
    public int maxTier() {
        int t = Palette.THATCH;
        for (int i = 1; i < Palette.TIER_RESEARCH.length; i++) if (has(Palette.TIER_RESEARCH[i])) t = i;
        return t;
    }

    public int scholarsWorking(long now) {
        scholars.values().removeIf(t -> now - t > 60);
        return Math.min(Research.MAX_SCHOLARS, scholars.size());
    }

    public @Nullable Building buildingWithSign(String dim, long sign) {
        for (Building b : buildings) if (b.dim.equals(dim) && b.sign.asLong() == sign) return b;
        return null;
    }

    public @Nullable Building building(@Nullable UUID id) {
        if (id == null) return null;
        for (Building b : buildings) if (b.id.equals(id)) return b;
        return null;
    }
}
