package com.dominionmod.dominion.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** A construction site: what's being built, where, and how far along it is. Saved with the kingdom data. */
public final class Site {
    public final UUID id;
    public final UUID kingdom;
    public final String structure;
    public final String dim;
    /** Buildings: the clicked ground block. Walls: the start point. */
    public final BlockPos origin;
    /** Walls: the end point (same as origin for buildings). */
    public final BlockPos end;
    public final int rotation;
    /** Walls: ground height of each column, fixed when the site was marked. */
    public final List<Integer> columns;
    public int cursor;
    public String status = "";

    public static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(s -> s.id),
            UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(s -> s.kingdom),
            Codec.STRING.fieldOf("structure").forGetter(s -> s.structure),
            Codec.STRING.fieldOf("dim").forGetter(s -> s.dim),
            BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
            BlockPos.CODEC.fieldOf("end").forGetter(s -> s.end),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(s -> s.rotation),
            Codec.INT.listOf().optionalFieldOf("columns", List.of()).forGetter(s -> s.columns),
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(s -> s.cursor)
    ).apply(i, Site::new));

    public Site(UUID id, UUID kingdom, String structure, String dim, BlockPos origin, BlockPos end, int rotation, List<Integer> columns, int cursor) {
        this.id = id;
        this.kingdom = kingdom;
        this.structure = structure;
        this.dim = dim;
        this.origin = origin;
        this.end = end;
        this.rotation = rotation;
        this.columns = new ArrayList<>(columns);
        this.cursor = cursor;
    }

    public String name() {
        Structures.Structure s = Structures.get(structure);
        return s == null ? structure : s.name();
    }
}
