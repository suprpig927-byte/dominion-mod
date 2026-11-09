package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.wild.Orc;
import com.dominionmod.dominion.entity.wild.OrcWarlord;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Left alone, an orc village grows: after a few days it's a war camp, after a few more a
 * stronghold behind a palisade with watch platforms and a Warlord in a great hut. Every night the
 * orcs may send a warband to raid the nearest town; the bigger the village, the more likely and the
 * bigger the raid. Raiders fall back when the sun comes up.
 */
public final class OrcWarbands {
    private OrcWarbands() {}

    private static final int[] CAP = {10, 14, 18};
    private static final float[] RAID_CHANCE = {0.15f, 0.35f, 0.55f};
    private static final int[] RAID_RANGE = {600, 750, 900};

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        long t = level.getGameTime();
        KingdomData d = Realm.data(level);
        long day = Diplomacy.day(level);
        long dayTime = level.getDayTime() % 24000L;
        boolean nightfall = dayTime >= 13000 && dayTime < 13400;
        if (t % 200 != 91 && !(nightfall && t % 40 == 11)) return;
        for (KingdomData.OrcVillage v : d.orcVillages) {
            if (v.conquered || !v.dim.equals(KingdomData.dim(level))) continue;
            try {
                if (v.lastDay < 0) v.lastDay = day;
                if (v.lastDay < day) {
                    v.lastDay = day;
                    v.days++;
                    d.markChanged();
                }
                if (awake(level, v.totem)) grow(level, d, v);
                if (nightfall && v.lastRaid < day) {
                    v.lastRaid = day;
                    d.markChanged();
                    maybeRaid(level, d, v);
                }
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Orc village at {} misbehaved", v.totem, e);
            }
        }
    }

    private static boolean awake(ServerLevel level, BlockPos at) {
        if (!level.isLoaded(at)) return false;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(at.getX(), p.getY(), at.getZ()) < 120 * 120) return true;
        return false;
    }

    public static int wantedStage(KingdomData.OrcVillage v) {
        return v.days >= 6 ? 2 : v.days >= 3 ? 1 : 0;
    }

    private static void grow(ServerLevel level, KingdomData d, KingdomData.OrcVillage v) {
        RandomSource r = level.getRandom();
        int want = wantedStage(v);
        if (want > v.stage) {
            v.stage = want;
            d.markChanged();
            if (want >= 1) palisade(level, v.totem, want >= 2);
            if (want >= 2) {
                watchPlatform(level, v.totem.offset(15, 0, 15));
                watchPlatform(level, v.totem.offset(-15, 0, -15));
                Kingdom paint = new Kingdom("Orcs", new UUID(0, 0), 12, 15, ItemStack.EMPTY);
                BlockPos hut = surface(level, v.totem.getX(), v.totem.getZ() - 11);
                Settlements.place(level, paint, "orc_warlord_hut", hut, 0, 0, false);
            }
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(v.totem.getX(), p.getY(), v.totem.getZ()) < 160 * 160)
                p.sendSystemMessage(Component.literal(want >= 2 ? "The orcs have raised a stronghold, and a Warlord rules it." :
                        "The orc village has grown into a war camp behind a palisade.").withStyle(ChatFormatting.DARK_GREEN));
        }
        List<Orc> orcs = level.getEntitiesOfClass(Orc.class, new AABB(v.totem).inflate(40), Orc::isAlive);
        if (v.stage >= 2 && !v.warlordSlain && orcs.stream().noneMatch(o -> o instanceof OrcWarlord)) {
            BlockPos at = surface(level, v.totem.getX(), v.totem.getZ() - 11).above();
            OrcWarlord w = ModContent.ORC_WARLORD.get().create(level, EntitySpawnReason.STRUCTURE);
            if (w != null) {
                w.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
                w.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
                w.setHomeTo(v.totem, 20);
                level.addFreshEntity(w);
            }
        }
        if (orcs.size() < CAP[v.stage] && level.getGameTime() % 2400 < 200) {
            for (int i = 0; i < 2 && orcs.size() + i < CAP[v.stage]; i++) {
                Orc o = ModContent.ORC.get().create(level, EntitySpawnReason.STRUCTURE);
                if (o == null) continue;
                double a = r.nextDouble() * Math.PI * 2, dist = 4 + r.nextDouble() * 10;
                BlockPos at = surface(level, v.totem.getX() + (int) (Math.cos(a) * dist), v.totem.getZ() + (int) (Math.sin(a) * dist)).above();
                o.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
                o.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
                o.setPersistenceRequired();
                o.setHomeTo(v.totem, 22);
                level.addFreshEntity(o);
            }
        }
    }

    private static void maybeRaid(ServerLevel level, KingdomData d, KingdomData.OrcVillage v) {
        Kingdom target = null;
        double best = RAID_RANGE[v.stage];
        for (Kingdom k : d.all()) {
            if (k.wild || k.hall.isEmpty() || !k.hallDim.equals(v.dim)) continue;
            double dist = Math.sqrt(k.hall.get().distSqr(v.totem));
            if (dist < best) {
                best = dist;
                target = k;
            }
        }
        if (target == null || level.getRandom().nextFloat() > RAID_CHANCE[v.stage]) return;
        for (Armies.Army a : d.armies) if (a.orcs() && a.target.equals(target.id) && !a.returning) return;
        Armies.raid(level, v.totem, target, 4 + 3 * v.stage, false);
    }

    /** The Warlord died: his stronghold is leaderless for good. */
    public static void warlordSlain(ServerLevel level, BlockPos at) {
        KingdomData d = Realm.data(level);
        for (KingdomData.OrcVillage v : d.orcVillages) {
            if (v.dim.equals(KingdomData.dim(level)) && v.totem.distSqr(at) < 80 * 80) {
                v.warlordSlain = true;
                d.markChanged();
            }
        }
    }

    // ------------------------------------------------------------------ fortifications

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    /** A ring of sharpened logs round the village, with four gaps for gates (a stronghold's is taller). */
    private static void palisade(ServerLevel level, BlockPos center, boolean tall) {
        int r = 22, h = tall ? 4 : 3;
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState(), tip = Blocks.SPRUCE_FENCE.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(dist - r) > 0.55) continue;
                if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) continue; // gates
                BlockPos g = surface(level, center.getX() + dx, center.getZ() + dz);
                if (!level.getFluidState(g).isEmpty()) continue;
                for (int y = 1; y <= h; y++) level.setBlock(g.above(y), log, Block.UPDATE_CLIENTS);
                level.setBlock(g.above(h + 1), (dx + dz) % 3 == 0 ? Blocks.SKELETON_SKULL.defaultBlockState() : tip, Block.UPDATE_ALL);
            }
        }
    }

    /** Four log stilts, a plank floor and a fence rail: a lookout's perch. */
    private static void watchPlatform(ServerLevel level, BlockPos near) {
        BlockPos g = surface(level, near.getX(), near.getZ());
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2)
            for (int y = 1; y <= 4; y++) level.setBlock(g.offset(dx, y, dz), Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(g.offset(dx, 5, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (dx != 0 || dz != 0) level.setBlock(g.offset(dx, 6, dz), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int y = 1; y <= 5; y++) level.setBlock(g.offset(0, y, 2), Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_CLIENTS);
        level.setBlock(g.offset(0, 7, 0), Blocks.SKELETON_SKULL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(g.offset(0, 6, 0), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Debug: grow a village straight to a stronghold. */
    public static void fastForward(ServerLevel level, KingdomData.OrcVillage v) {
        v.days = Math.max(v.days, 6);
        grow(level, Realm.data(level), v);
    }
}
