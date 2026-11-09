package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An envoy walks up to the member of the other kingdom they were sent to, says their piece,
 * waits a while for an answer, then turns for home and is gone.
 */
public class EnvoyGoal extends Goal {
    private final Citizen c;

    public EnvoyGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return c.onEnvoyErrand();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (c.envoyStale()) {
            c.discard();
            return;
        }
        c.tickEnvoy();
        Player p = c.envoyFor() == null ? null : c.level().getPlayerByUUID(c.envoyFor());
        if (p == null || !p.isAlive()) {
            if (c.envoyTicks() > 400) c.discard();
            return;
        }
        if (c.isEnvoy()) {
            c.getLookControl().setLookAt(p, 30f, 30f);
            if (c.distanceToSqr(p) < 3.5 * 3.5) {
                c.getNavigation().stop();
                c.deliverEnvoy();
            } else if (c.envoyTicks() > 20 * 45) {
                c.deliverEnvoy(); // they call out from where they are
            } else if (c.envoyTicks() % 20 == 1) {
                c.getNavigation().moveTo(p, 0.62);
            }
            return;
        }
        // spoken: wait for an answer, then leave
        if (c.envoyTicks() < 20 * 25) {
            c.getNavigation().stop();
            c.getLookControl().setLookAt(p, 30f, 30f);
            return;
        }
        if (c.envoyTicks() % 40 == 0) {
            Vec3 away = c.position().subtract(p.position()).normalize().scale(16).add(c.position());
            c.getNavigation().moveTo(away.x, away.y, away.z, 0.6);
        }
        if (c.distanceToSqr(p) > 40 * 40 || c.envoyTicks() > 20 * 70) c.discard();
    }
}
