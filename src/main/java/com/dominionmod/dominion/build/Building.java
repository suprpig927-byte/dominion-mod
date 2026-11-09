package com.dominionmod.dominion.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** A finished building: what it is, where, its tier, and the sign out front you click to upgrade it. */
public final class Building {
    public final UUID id;
    public String structure;
    public final String dim;
    public final BlockPos origin;
    public final int rotation;
    public int tier;
    public BlockPos sign;
    /** Citizens working here (a workshop's hands, a watchtower's garrison). */
    public final java.util.List<UUID> workers = new java.util.ArrayList<>();
    /** Workshops can be closed from the Industry tab: nobody is hired and nothing is made. */
    public boolean open = true;
    /** What it made last (for the Industry tab), and when. Not saved. */
    public String lastOutput = "";
    public String problem = "";

    public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(b -> b.id),
            Codec.STRING.fieldOf("structure").forGetter(b -> b.structure),
            Codec.STRING.fieldOf("dim").forGetter(b -> b.dim),
            BlockPos.CODEC.fieldOf("origin").forGetter(b -> b.origin),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(b -> b.rotation),
            Codec.INT.optionalFieldOf("tier", 1).forGetter(b -> b.tier),
            BlockPos.CODEC.fieldOf("sign").forGetter(b -> b.sign),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("workers", java.util.List.of()).forGetter(b -> b.workers),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("open", true).forGetter(b -> b.open)
    ).apply(i, Building::new));

    private Building(UUID id, String structure, String dim, BlockPos origin, int rotation, int tier, BlockPos sign, java.util.List<UUID> workers, boolean open) {
        this(id, structure, dim, origin, rotation, tier, sign);
        this.workers.addAll(workers);
        this.open = open;
    }

    /** The middle of the building's footprint (at ground level). */
    public BlockPos center() {
        Structures.Structure s = Structures.get(structure);
        if (s == null) return origin;
        return Plans.cell(s, origin, rotation, s.width() / 2, s.sink(), s.depth() / 2);
    }

    public Building(UUID id, String structure, String dim, BlockPos origin, int rotation, int tier, BlockPos sign) {
        this.id = id;
        this.structure = structure;
        this.dim = dim;
        this.origin = origin;
        this.rotation = rotation;
        this.tier = tier;
        this.sign = sign;
    }

    public String name() {
        Structures.Structure s = Structures.get(structure);
        return s == null ? structure : s.name();
    }
}
