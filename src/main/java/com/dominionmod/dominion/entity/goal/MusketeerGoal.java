package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.item.FirearmItem;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Musketeers hold at range, take aim, fire, then spend a while reloading. */
public class MusketeerGoal extends Goal {
    private final Citizen c;
    private int reload;
    private int aim;

    public MusketeerGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private FirearmItem gun() {
        return c.getMainHandItem().getItem() instanceof FirearmItem g ? g : null;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = c.getTarget();
        return c.job() == Job.MUSKETEER && gun() != null && t != null && t.isAlive() && c.hasAmmo();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
        aim = 0;
    }

    @Override
    public void tick() {
        LivingEntity t = c.getTarget();
        FirearmItem gun = gun();
        if (t == null || gun == null) return;
        boolean musket = gun.velocity > 4.0f;
        double range = musket ? 22 : 12, keep = musket ? 9 : 5;
        c.getLookControl().setLookAt(t, 30f, 30f);
        double d = c.distanceTo(t);
        boolean sees = c.getSensing().hasLineOfSight(t);
        if (d > range - 2 || !sees) c.getNavigation().moveTo(t, 0.75);
        else if (d < keep - 2) {
            double dx = c.getX() - t.getX(), dz = c.getZ() - t.getZ();
            c.getNavigation().moveTo(c.getX() + dx, c.getY(), c.getZ() + dz, 0.8);
        } else c.getNavigation().stop();

        if (reload > 0) {
            reload--;
            if (reload == 1) c.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.8f, 0.8f);
            return;
        }
        if (d <= range && sees) {
            if (++aim >= 12 && c.level() instanceof ServerLevel level) {
                double dx = t.getX() - c.getX(), dz = t.getZ() - c.getZ();
                double dy = t.getY(0.5) - c.getEyeY();
                float yRot = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
                float xRot = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
                c.swingForAttack(InteractionHand.MAIN_HAND);
                FirearmItem.fire(level, c, xRot, yRot, gun);
                c.spendAmmo();
                reload = gun.loadTicks * 2;
                aim = 0;
            }
        } else aim = 0;
    }
}
