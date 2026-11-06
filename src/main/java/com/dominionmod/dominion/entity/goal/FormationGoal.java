package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Rallied soldiers fall in behind their commander: two abreast in a column while the commander
 * moves, and spread into a battle line (ranks of up to 8) when the commander stops.
 */
public class FormationGoal extends Goal {
    private final Citizen c;
    private int repath;

    public FormationGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return c.order() == Order.RALLY && c.commanderPlayer() != null && c.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
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
        Player cmd = c.commanderPlayer();
        if (cmd == null) return;
        Vec3 slot = slot(cmd);
        double dist = c.position().distanceToSqr(slot);
        if (dist > 48 * 48) {
            // Hopelessly left behind (or the commander teleported): catch up.
            c.snapTo(slot.x, cmd.getY(), slot.z, cmd.getYRot(), 0);
            c.getNavigation().stop();
            return;
        }
        if (dist > 1.2) {
            if (--repath <= 0 || c.getNavigation().isDone()) {
                repath = 5;
                double speed = dist > 64 ? 1.0 : dist > 9 ? 0.8 : 0.6;
                c.getNavigation().moveTo(slot.x, cmd.getY(), slot.z, speed);
            }
            c.getLookControl().setLookAt(slot.x, c.getEyeY(), slot.z);
        } else {
            c.getNavigation().stop();
            // Face the same way as the commander.
            Vec3 fwd = forward(cmd);
            c.getLookControl().setLookAt(c.getX() + fwd.x * 5, c.getEyeY(), c.getZ() + fwd.z * 5);
        }
    }

    private static Vec3 forward(Player p) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** Where this soldier should stand relative to the commander right now. */
    private Vec3 slot(Player cmd) {
        Vec3 fwd = forward(cmd);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        int i = c.formationIndex();
        int n = c.formationSize();
        double moved = Math.hypot(cmd.getX() - cmd.xo, cmd.getZ() - cmd.zo);
        double back, side;
        if (moved > 0.04) {
            int row = i / 2, col = i % 2;
            back = 2.5 + row * 1.6;
            side = col == 0 ? -0.8 : 0.8;
            if (n % 2 == 1 && i == n - 1) side = 0;
        } else {
            int width = Math.min(n, 8);
            int row = i / width, col = i % width;
            int inRow = Math.min(width, n - row * width);
            back = 2.5 + row * 1.8;
            side = (col - (inRow - 1) / 2.0) * 1.4;
        }
        return cmd.position().subtract(fwd.scale(back)).add(right.scale(side));
    }
}
