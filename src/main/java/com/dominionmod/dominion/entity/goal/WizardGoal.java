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
import com.dominionmod.dominion.magic.Spell;
import com.dominionmod.dominion.magic.Spells;
import net.minecraft.world.phys.Vec3;

/** Wizards keep their distance and cast from their spell book: fireballs, fangs, lightning, frost and healing. */
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
        if (d <= RANGE + 6 && c.castReady() && c.hasLineOfSight(t) && c.level() instanceof ServerLevel level) {
            Spell spell = choose(level, t, d);
            c.swingForAttack(InteractionHand.MAIN_HAND);
            Vec3 aim = new Vec3(t.getX() - c.getX(), t.getEyeY() - c.getEyeY(), t.getZ() - c.getZ());
            if (Spells.cast(level, c, spell, aim, t)) {
                level.sendParticles(ParticleTypes.WITCH, c.getX(), c.getY() + 2.0, c.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            }
            c.startCast(40);
        }
    }

    /**
     * Picks a spell for the moment: heal hurt friends, freeze a crowd, strike far targets with
     * lightning, otherwise fangs or fireballs.
     */
    private Spell choose(ServerLevel level, LivingEntity t, double d) {
        boolean ready;
        if (Spells.cooldownLeft(c, Spell.HEAL) == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, c.getBoundingBox().inflate(8), e -> Spells.ally(c, e))) {
                if (e.getHealth() < e.getMaxHealth() * 0.6f) return Spell.HEAL;
            }
        }
        int close = level.getEntitiesOfClass(LivingEntity.class, c.getBoundingBox().inflate(5),
                e -> e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null && !Spells.ally(c, e)).size();
        if (close >= 2 && Spells.cooldownLeft(c, Spell.FROST_NOVA) == 0) return Spell.FROST_NOVA;
        if (d > 9 && Spells.cooldownLeft(c, Spell.LIGHTNING) == 0) return Spell.LIGHTNING;
        ready = Spells.cooldownLeft(c, Spell.FANGS) == 0;
        return ready && d < 13 && c.getRandom().nextBoolean() ? Spell.FANGS : Spell.FIREBALL;
    }
}
