package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.EvokerFangs;

/** Wizards keep their distance and conjure a line of fangs that bursts up under their target. */
public class WizardGoal extends Goal {
    private static final double RANGE = 14, KEEP = 7;
    private final Citizen c;

    public WizardGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = c.getTarget();
        return c.job() == Job.WIZARD && t != null && t.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = c.getTarget();
        c.tickCast();
        if (t == null) return;
        c.getLookControl().setLookAt(t, 30f, 30f);
        double d = c.distanceTo(t);
        if (d > RANGE - 2) c.getNavigation().moveTo(t, 0.7);
        else if (d < KEEP - 3) {
            double dx = c.getX() - t.getX(), dz = c.getZ() - t.getZ();
            c.getNavigation().moveTo(c.getX() + dx, c.getY(), c.getZ() + dz, 0.8);
        } else c.getNavigation().stop();
        if (d <= RANGE && c.castReady() && c.hasLineOfSight(t) && c.level() instanceof ServerLevel level) {
            cast(level, t);
            c.startCast(50);
        }
    }

    private void cast(ServerLevel level, LivingEntity t) {
        c.swingForAttack(InteractionHand.MAIN_HAND);
        float yaw = (float) Mth.atan2(t.getZ() - c.getZ(), t.getX() - c.getX());
        double dist = Math.min(RANGE, c.distanceTo(t) + 2);
        for (int i = 0; i < (int) (dist / 1.25); i++) {
            double h = 1.25 * (i + 1);
            double x = c.getX() + Mth.cos(yaw) * h, z = c.getZ() + Mth.sin(yaw) * h;
            BlockPos ground = BlockPos.containing(x, Math.max(t.getY(), c.getY()) + 1, z);
            for (int k = 0; k < 6 && level.getBlockState(ground.below()).canBeReplaced(); k++) ground = ground.below();
            level.addFreshEntity(new EvokerFangs(level, x, ground.getY(), z, yaw, i, c));
        }
        level.sendParticles(ParticleTypes.WITCH, c.getX(), c.getY() + 2.0, c.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, c.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, c.getSoundSource(), 1.0f, 1.2f);
    }
}
