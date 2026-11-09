package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Armies;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * When an enemy army is at the gates, archers, musketeers and wizards climb the walls and towers
 * and shoot down from the battlements. A "battlement" is any spot on top of the kingdom's walls,
 * towers or keep, at least three blocks above the ground beside it, that they can climb up to.
 */
public class BattlementGoal extends Goal {
    private static final Map<UUID, List<BlockPos>> SPOTS = new HashMap<>();
    private static final Map<UUID, Long> FOUND = new HashMap<>();
    private static final Map<UUID, Set<Long>> TAKEN = new HashMap<>();

    private final Citizen c;
    private @Nullable BlockPos spot;
    private int repath;

    public BattlementGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean shooter() {
        Job j = c.job();
        return j == Job.ARCHER || j == Job.MUSKETEER || j == Job.WIZARD;
    }

    @Override
    public boolean canUse() {
        if (!shooter() || c.order() != Order.DUTY || c.isPassenger() || c.tickCount % 40 != 0 || !(c.level() instanceof ServerLevel level)) return false;
        Kingdom k = c.kingdom();
        if (k == null || k.hall.isEmpty() || !Armies.underAttack(level, k)) return false;
        spot = claim(level, k);
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (spot == null || !(c.level() instanceof ServerLevel level)) return false;
        Kingdom k = c.kingdom();
        return k != null && c.order() == Order.DUTY && Armies.underAttack(level, k);
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        Kingdom k = c.kingdom();
        if (spot != null && k != null) TAKEN.computeIfAbsent(k.id, x -> new HashSet<>()).remove(spot.asLong());
        spot = null;
    }

    @Override
    public void tick() {
        if (spot == null || --repath > 0) return;
        repath = 20;
        Vec3 at = Vec3.atBottomCenterOf(spot);
        if (c.distanceToSqr(at) > 1.2) c.getNavigation().moveTo(at.x, at.y, at.z, 0.6);
        else c.getNavigation().stop();
    }

    private @Nullable BlockPos claim(ServerLevel level, Kingdom k) {
        long now = level.getGameTime();
        Long when = FOUND.get(k.id);
        if (when == null || now - when > 1200) {
            SPOTS.put(k.id, find(level, k));
            FOUND.put(k.id, now);
            TAKEN.remove(k.id);
        }
        Set<Long> taken = TAKEN.computeIfAbsent(k.id, x -> new HashSet<>());
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : SPOTS.getOrDefault(k.id, List.of())) {
            if (taken.contains(p.asLong())) continue;
            double d = c.distanceToSqr(Vec3.atCenterOf(p));
            if (d < bd && d < 64 * 64) {
                Path path = c.getNavigation().createPath(p, 0);
                if (path == null || !path.canReach()) continue;
                bd = d;
                best = p;
            }
        }
        if (best != null) taken.add(best.asLong());
        return best;
    }

    /** Wall-tops and tower roofs near the Town Hall. */
    private static List<BlockPos> find(ServerLevel level, Kingdom k) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos hall = k.hall.get();
        KingdomData d = Realm.data(level);
        for (int dx = -44; dx <= 44; dx++) {
            for (int dz = -44; dz <= 44; dz++) {
                int x = hall.getX() + dx, z = hall.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos stand = new BlockPos(x, top, z);
                if (d.ownerAt(level, stand) != k) continue;
                var below = level.getBlockState(stand.below());
                if (!below.isFaceSturdy(level, stand.below(), net.minecraft.core.Direction.UP) || below.getBlock() instanceof net.minecraft.world.level.block.StairBlock) continue;
                if (!level.getBlockState(stand.above()).isAir()) continue;
                int drop = 0;
                for (int[] n : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                    int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + n[0], z + n[1]);
                    drop = Math.max(drop, top - ny);
                }
                if (drop >= 3 && top - level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) <= 1 && (dx * 7 + dz * 3) % 2 == 0) out.add(stand);
            }
        }
        return out;
    }
}
