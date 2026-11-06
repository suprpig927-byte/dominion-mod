package com.dominionmod.dominion.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** One kingdom: its name, colors, flag, people and treasury of Influence. Claims live in {@link KingdomData}. */
public final class Kingdom {
    public final UUID id;
    public String name;
    public UUID owner;
    public final Set<UUID> members = new LinkedHashSet<>();
    public int primary;
    public int secondary;
    public ItemStack flag;
    public Optional<BlockPos> hall;
    public String hallDim;
    public double influence;
    /** Citizen UUID -> job ordinal, so counts work even while citizens are in unloaded chunks. */
    public final Map<UUID, Integer> citizens = new HashMap<>();
    public boolean peaceful;
    /** Bed (head block, packed) -> the citizen who sleeps there. */
    public final Map<Long, UUID> beds = new HashMap<>();
    /** Citizen -> name, for the roster. */
    public final Map<UUID, String> names = new HashMap<>();

    public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(k -> k.id),
            Codec.STRING.fieldOf("name").forGetter(k -> k.name),
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(k -> k.owner),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(k -> new ArrayList<>(k.members)),
            Codec.INT.optionalFieldOf("primary", 14).forGetter(k -> k.primary),
            Codec.INT.optionalFieldOf("secondary", 4).forGetter(k -> k.secondary),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("flag", ItemStack.EMPTY).forGetter(k -> k.flag),
            BlockPos.CODEC.optionalFieldOf("hall").forGetter(k -> k.hall),
            Codec.STRING.optionalFieldOf("hall_dim", "minecraft:overworld").forGetter(k -> k.hallDim),
            Codec.DOUBLE.optionalFieldOf("influence", 0.0).forGetter(k -> k.influence),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("citizens", Map.of()).forGetter(Kingdom::citizensAsStrings),
            Codec.BOOL.optionalFieldOf("peaceful", true).forGetter(k -> k.peaceful),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("beds", Map.of()).forGetter(Kingdom::bedsAsStrings),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(Kingdom::namesAsStrings)
    ).apply(i, Kingdom::new));

    private Kingdom(UUID id, String name, UUID owner, List<UUID> members, int primary, int secondary, ItemStack flag,
                    Optional<BlockPos> hall, String hallDim, double influence, Map<String, Integer> citizens, boolean peaceful,
                    Map<String, String> beds, Map<String, String> names) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members.addAll(members);
        this.members.add(owner);
        this.primary = primary;
        this.secondary = secondary;
        this.flag = flag;
        this.hall = hall;
        this.hallDim = hallDim;
        this.influence = influence;
        citizens.forEach((k, v) -> {
            try {
                this.citizens.put(UUID.fromString(k), v);
            } catch (IllegalArgumentException ignored) {
            }
        });
        this.peaceful = peaceful;
        beds.forEach((k, v) -> {
            try {
                this.beds.put(Long.parseLong(k), UUID.fromString(v));
            } catch (IllegalArgumentException ignored) {
            }
        });
        names.forEach((k, v) -> {
            try {
                this.names.put(UUID.fromString(k), v);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    public Kingdom(String name, UUID owner, int primary, int secondary, ItemStack flag) {
        this(UUID.randomUUID(), name, owner, List.of(owner), primary, secondary, flag, Optional.empty(), "minecraft:overworld", 0, Map.of(), true, Map.of(), Map.of());
    }

    private Map<String, Integer> citizensAsStrings() {
        Map<String, Integer> out = new HashMap<>();
        citizens.forEach((k, v) -> out.put(k.toString(), v));
        return out;
    }

    private Map<String, String> bedsAsStrings() {
        Map<String, String> out = new HashMap<>();
        beds.forEach((k, v) -> out.put(Long.toString(k), v.toString()));
        return out;
    }

    private Map<String, String> namesAsStrings() {
        Map<String, String> out = new HashMap<>();
        names.forEach((k, v) -> out.put(k.toString(), v));
        return out;
    }

    /** The bed this citizen sleeps in, if any. */
    public @org.jspecify.annotations.Nullable BlockPos bedOf(UUID citizen) {
        for (var e : beds.entrySet()) if (e.getValue().equals(citizen)) return BlockPos.of(e.getKey());
        return null;
    }

    public DyeColor primaryDye() {
        return DyeColor.byId(primary);
    }

    public DyeColor secondaryDye() {
        return DyeColor.byId(secondary);
    }

    /** 0xRRGGBB text color for this kingdom's name. */
    public int textColor() {
        return primaryDye().getTextColor();
    }

    public int countJob(int jobOrdinal) {
        int n = 0;
        for (int j : citizens.values()) if (j == jobOrdinal) n++;
        return n;
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }
}
