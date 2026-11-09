package com.dominionmod.dominion.entity.wild;

import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Brings the wild to life around players: herds of deer in forests and taiga; Mustangs,
 * Clydesdales and Shetland ponies on plains, meadows and savannas; and centaur bands roaming the
 * open grasslands. Each kind is kept to a few nearby so the land never fills up.
 */
public final class WildLife {
    private WildLife() {}

    private record Kind(EntityType<? extends Mob> type, int min, int max, int cap, int weight) {}

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || level.getGameTime() % 300 != 150) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double a = r.nextDouble() * Math.PI * 2, dist = 28 + r.nextDouble() * 36;
            int x = p.getBlockX() + (int) (Math.cos(a) * dist), z = p.getBlockZ() + (int) (Math.sin(a) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(ground.below()).isSource()) {
                otters(level, ground, r);
                continue;
            }
            if (!level.getBlockState(ground.below()).is(Blocks.GRASS_BLOCK) && !level.getBlockState(ground.below()).is(Blocks.PODZOL)
                    && !level.getBlockState(ground.below()).is(Blocks.SNOW_BLOCK)) continue;
            List<Kind> kinds = kindsFor(level.getBiome(ground));
            if (kinds.isEmpty()) continue;
            int total = 0;
            for (Kind k : kinds) total += k.weight();
            int pick = r.nextInt(total);
            Kind kind = kinds.get(0);
            for (Kind k : kinds) {
                if ((pick -= k.weight()) < 0) {
                    kind = k;
                    break;
                }
            }
            int nearby = level.getEntities(kind.type(), new AABB(ground).inflate(96), e -> true).size();
            if (nearby >= kind.cap()) continue;
            int n = kind.min() + r.nextInt(kind.max() - kind.min() + 1);
            for (int i = 0; i < n; i++) {
                int sx = x + r.nextInt(7) - 3, sz = z + r.nextInt(7) - 3;
                BlockPos at = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
                if (!level.getBlockState(at).isAir() || !level.getFluidState(at.below()).isEmpty()) continue;
                Mob m = kind.type().create(level, EntitySpawnReason.NATURAL);
                if (m == null) continue;
                m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
                if (!level.noCollision(m)) continue;
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
                level.addFreshEntity(m);
            }
        }
    }

    /** Otter families live along rivers, in swamps and mangroves, and on the shores of lakes in the forest. */
    private static void otters(ServerLevel level, BlockPos surface, RandomSource r) {
        Holder<Biome> b = level.getBiome(surface);
        boolean home = b.is(BiomeTags.IS_RIVER) || b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP)
                || (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA)) && r.nextInt(3) == 0;
        if (!home || b.is(Biomes.FROZEN_RIVER) || r.nextInt(3) != 0) return;
        if (level.getEntities(ModContent.OTTER.get(), new AABB(surface).inflate(96), e -> true).size() >= 6) return;
        int n = 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            BlockPos at = surface.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            at = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
            if (!level.getFluidState(at.below()).isSource() || !level.getBlockState(at).isAir()) continue;
            var o = ModContent.OTTER.get().create(level, EntitySpawnReason.NATURAL);
            if (o == null) continue;
            o.snapTo(at.getX() + 0.5, at.getY() - 0.4, at.getZ() + 0.5, r.nextFloat() * 360, 0);
            o.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
            if (i == n - 1 && r.nextBoolean()) o.setAge(-24000);
            level.addFreshEntity(o);
        }
    }

    private static List<Kind> kindsFor(Holder<Biome> b) {
        List<Kind> out = new ArrayList<>();
        boolean plains = b.is(Biomes.PLAINS) || b.is(Biomes.SUNFLOWER_PLAINS) || b.is(Biomes.MEADOW);
        boolean savanna = b.is(BiomeTags.IS_SAVANNA);
        if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(Biomes.MEADOW) || b.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            out.add(new Kind(ModContent.DEER.get(), 2, 4, 8, 6));
        }
        if (plains || savanna) {
            out.add(new Kind(ModContent.MUSTANG.get(), 2, 5, 8, 4));
            out.add(new Kind(ModContent.CENTAUR.get(), 2, 4, 5, 2));
        }
        if (plains || b.is(BiomeTags.IS_TAIGA)) out.add(new Kind(ModContent.CLYDESDALE.get(), 1, 3, 4, 2));
        if (plains || b.is(Biomes.WINDSWEPT_HILLS)) out.add(new Kind(ModContent.SHETLAND_PONY.get(), 1, 3, 4, 2));
        return out;
    }
}
