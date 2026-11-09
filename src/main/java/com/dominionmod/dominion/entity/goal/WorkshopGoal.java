package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Industry;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** Miners, millers, bakers and the other trades spend the working day at their workshop. */
public class WorkshopGoal extends Goal {
    private final Citizen c;
    private @Nullable BlockPos spot;
    private int ticks;

    public WorkshopGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!c.job().trade() || c.order() != Order.DUTY || c.tickCount % 40 != 0 || !c.level().isBrightOutside()
                || !(c.level() instanceof ServerLevel level)) return false;
        Building b = Industry.workplaceOf(level, c);
        if (b == null) return false;
        BlockPos center = b.center();
        spot = center.offset(c.getRandom().nextInt(5) - 2, 0, c.getRandom().nextInt(5) - 2);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && c.job().trade() && c.order() == Order.DUTY && c.level().isBrightOutside() && ticks < 600 && c.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        if (spot != null) c.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.5);
    }

    @Override
    public void tick() {
        ticks++;
        if (spot == null) return;
        if (c.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 4) {
            if (ticks % 60 == 0) c.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.5);
            return;
        }
        c.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (ticks % 25 == 0) c.swingForAttack(InteractionHand.MAIN_HAND);
        if (ticks % 120 == 0) spot = spot.offset(c.getRandom().nextInt(3) - 1, 0, c.getRandom().nextInt(3) - 1);
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }
}
