package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Watchtowers;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * An archer posted to a watchtower walks to its foot, climbs to the top, and stays there on watch,
 * shooting anything hostile in sight (further than from the ground, and harder).
 */
public class GarrisonGoal extends Goal {
    private final Citizen c;
    private @Nullable BlockPos post;
    private int repath, stuck, shot;

    public GarrisonGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        post = Watchtowers.postOf(c);
        return post != null;
    }

    @Override
    public boolean canContinueToUse() {
        post = Watchtowers.postOf(c);
        return post != null && c.isAlive();
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
        if (post == null || !(c.level() instanceof ServerLevel level)) return;
        double dx = c.getX() - (post.getX() + 0.5), dz = c.getZ() - (post.getZ() + 0.5), dy = c.getY() - post.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.2 && Math.abs(dy) < 1.5) {
            c.getNavigation().stop();
            shoot(level);
            return;
        }
        // at the tower's foot (or stuck): climb up
        if (flat < 7 || ++stuck > 400) {
            c.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
            level.playSound(null, post, SoundEvents.LADDER_STEP, SoundSource.NEUTRAL, 1f, 1f);
            stuck = 0;
            return;
        }
        if (--repath <= 0) {
            repath = 40;
            c.getNavigation().moveTo(post.getX() + 0.5, post.getY() - 6, post.getZ() + 0.5, 0.6);
        }
    }

    private void shoot(ServerLevel level) {
        if (shot > 0) shot--;
        var k = c.kingdom();
        if (k == null) return;
        LivingEntity best = c.getTarget();
        if (best == null || !best.isAlive() || best.distanceToSqr(c) > 40 * 40) {
            best = null;
            double bd = 40 * 40;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c.blockPosition()).inflate(40, 24, 40),
                    e -> Watchtowers.hostileTo(level, k, e) && c.hasLineOfSight(e))) {
                double d = e.distanceToSqr(c);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best != null) c.setTarget(best);
        }
        if (best == null) return;
        c.getLookControl().setLookAt(best, 30f, 30f);
        if (shot <= 0 && c.hasLineOfSight(best)) {
            shot = 30;
            c.performRangedAttack(best, 1.0f);
            c.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
}
