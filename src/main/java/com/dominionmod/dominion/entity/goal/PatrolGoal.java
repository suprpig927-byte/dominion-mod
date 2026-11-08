package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Patrolling soldiers walk their route waypoint to waypoint, pausing briefly at each, round and round. */
public class PatrolGoal extends Goal {
    private final Citizen c;
    private int pause, repath;

    public PatrolGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return c.order() == Order.PATROL && !c.patrolRoute().isEmpty() && c.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos wp = c.patrolRoute().get(c.patrolIndex());
        Vec3 at = Vec3.atBottomCenterOf(wp);
        if (c.distanceToSqr(at) < 2.5 * 2.5) {
            c.getNavigation().stop();
            if (++pause > 30) {
                pause = 0;
                c.nextWaypoint();
                repath = 0;
            }
            return;
        }
        if (--repath <= 0) {
            repath = 20;
            c.getNavigation().moveTo(at.x, at.y, at.z, 0.6);
        }
    }
}
