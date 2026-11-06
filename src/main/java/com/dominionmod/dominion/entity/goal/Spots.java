package com.dominionmod.dominion.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Finding somewhere to stand next to a block (you can't walk into a log or a Town Hall). */
final class Spots {
    private Spots() {}

    static BlockPos standNear(Level level, BlockPos target, Entity who) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 1; r <= 2 && best == null; r++) {
            for (BlockPos p : BlockPos.betweenClosed(target.offset(-r, -1, -r), target.offset(r, 1, r))) {
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
                if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
                if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue;
                double d = who.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best != null ? best : target;
    }
}
