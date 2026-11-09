package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * An army on campaign: march across country toward the objective in legs of a dozen blocks or so
 * (pathfinding can't plan the whole road at once), and stand ready once there. Fighting comes
 * first: the goal steps aside whenever there's an enemy to fight.
 */
public class MarchGoal extends Goal {
    private final Citizen c;
    private int repath, stuck;
    private Vec3 last = Vec3.ZERO;

    public MarchGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return c.order() == Order.MARCH && c.anchor() != null && c.getTarget() == null && !c.onEnvoyErrand();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
        stuck = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos goal = c.anchor();
        if (goal == null || --repath > 0) return;
        repath = 20;
        double dx = goal.getX() + 0.5 - c.getX(), dz = goal.getZ() + 0.5 - c.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 5) {
            c.getNavigation().stop();
            return;
        }
        if (c.position().distanceToSqr(last) < 1.0) stuck++;
        else stuck = 0;
        last = c.position();
        double leg = Math.min(dist, 14);
        double tx = c.getX() + dx / dist * leg, tz = c.getZ() + dz / dist * leg;
        if (stuck > 2) {
            // sidestep whatever is in the way
            double a = c.getRandom().nextDouble() * Math.PI * 2;
            tx += Math.cos(a) * 6;
            tz += Math.sin(a) * 6;
        }
        int ty = c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
        com.dominionmod.dominion.entity.Cavalry.moveTo(c, tx, ty, tz, 0.6);
    }
}
