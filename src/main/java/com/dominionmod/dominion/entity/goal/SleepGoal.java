package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

/** At night, citizens on duty walk home to their own bed and sleep until morning. */
public class SleepGoal extends Goal {
    private final Citizen c;
    private BlockPos bed, stand;
    private int ticks;

    public SleepGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (c.isSleeping() || c.order() != Order.DUTY || c.getTarget() != null || c.level().isBrightOutside()) return false;
        if (c.tickCount % 20 != 0) return false;
        bed = c.home();
        return bed != null && c.level().getBlockState(bed).getBlock() instanceof BedBlock
                && c.distanceToSqr(Vec3.atCenterOf(bed)) < 128 * 128;
    }

    @Override
    public void start() {
        ticks = 0;
        stand = Spots.standNear(c.level(), bed, c);
        c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
    }

    @Override
    public boolean canContinueToUse() {
        return bed != null && !c.isSleeping() && c.order() == Order.DUTY && c.getTarget() == null && !c.level().isBrightOutside() && ticks < 1200;
    }

    @Override
    public void tick() {
        if (bed == null) return;
        ticks++;
        if (c.distanceToSqr(Vec3.atCenterOf(bed)) < 2.2 * 2.2) {
            c.getNavigation().stop();
            if (c.level().getBlockState(bed).getBlock() instanceof BedBlock && !c.level().getBlockState(bed).getValue(BedBlock.OCCUPIED)) {
                c.startSleeping(bed);
            }
            bed = null;
            return;
        }
        if (ticks % 40 == 0) c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }
}
