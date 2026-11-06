package com.dominionmod.dominion.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** A finished building: what it is, where, its tier, and the sign out front you click to upgrade it. */
public final class Building {
    public final UUID id;
    public final String structure;
    public final String dim;
    public final BlockPos origin;
    public final int rotation;
    public int tier;
    public BlockPos sign;

    public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(b -> b.id),
            Codec.STRING.fieldOf("structure").forGetter(b -> b.structure),
            Codec.STRING.fieldOf("dim").forGetter(b -> b.dim),
            BlockPos.CODEC.fieldOf("origin").forGetter(b -> b.origin),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(b -> b.rotation),
            Codec.INT.optionalFieldOf("tier", 1).forGetter(b -> b.tier),
            BlockPos.CODEC.fieldOf("sign").forGetter(b -> b.sign)
    ).apply(i, Building::new));

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
