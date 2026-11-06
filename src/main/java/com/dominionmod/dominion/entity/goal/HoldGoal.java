package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps citizens near where they belong: the spot they were ordered to hold, their guard post,
 * or (for workers at night) close to their work spot.
 */
public class HoldGoal extends Goal {
    private final Citizen c;
    private BlockPos target;
    private int repath;

    public HoldGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private double radius() {
        if (c.order() == Order.HOLD) return 1.5;
        Job job = c.job();
        if (job.soldier) return 8;
        if (job.worker()) return c.level().isBrightOutside() ? 24 : 5;
        return 10;
    }

    private BlockPos where() {
        if (c.order() == Order.HOLD) return c.anchor();
        if (c.order() == Order.RALLY) return null;
        return c.post();
    }

    @Override
    public boolean canUse() {
        if (c.getTarget() != null) return false;
        target = where();
        if (target == null) return false;
        double r = radius();
        return c.distanceToSqr(target.getCenter()) > r * r;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || c.getTarget() != null || where() == null) return false;
        double r = Math.max(1.0, radius() * 0.5);
        return c.distanceToSqr(target.getCenter()) > r * r;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        if (--repath <= 0) {
            repath = 20;
            c.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, c.order() == Order.HOLD ? 0.8 : 0.6);
        }
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }
}
