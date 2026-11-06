package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Progress;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Scholars study at the nearest Research Table while the kingdom is researching something. */
public class ScholarGoal extends Goal {
    private final Citizen c;
    private BlockPos table, stand;
    private int ticks;

    public ScholarGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (c.job() != Job.SCHOLAR || c.order() != Order.DUTY || !c.level().isBrightOutside() || c.tickCount % 40 != 0) return false;
        if (!(c.level() instanceof ServerLevel level)) return false;
        Kingdom k = c.kingdom();
        if (k == null) return false;
        Progress pr = Realm.data(level).progress(k);
        if (pr.current.isEmpty()) return false;
        table = null;
        double best = 96 * 96;
        var it = pr.tables.iterator();
        while (it.hasNext()) {
            BlockPos p = BlockPos.of(it.next());
            if (!level.isLoaded(p)) continue;
            if (!level.getBlockState(p).is(ModContent.RESEARCH_TABLE.get())) {
                it.remove();
                continue;
            }
            double d = c.distanceToSqr(Vec3.atCenterOf(p));
            if (d < best) {
                best = d;
                table = p;
            }
        }
        return table != null;
    }

    @Override
    public void start() {
        ticks = 0;
        stand = Spots.standNear(c.level(), table, c);
        c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
        c.markWorked();
    }

    @Override
    public boolean canContinueToUse() {
        return table != null && c.job() == Job.SCHOLAR && c.order() == Order.DUTY && c.getTarget() == null && c.level().isBrightOutside() && ticks < 2400;
    }

    @Override
    public void tick() {
        if (table == null || !(c.level() instanceof ServerLevel level)) return;
        ticks++;
        if (c.distanceToSqr(Vec3.atCenterOf(table)) > 3.0 * 3.0) {
            if (ticks % 40 == 0) c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
            return;
        }
        c.getNavigation().stop();
        c.getLookControl().setLookAt(Vec3.atCenterOf(table));
        Kingdom k = c.kingdom();
        if (k == null) return;
        Progress pr = Realm.data(level).progress(k);
        if (pr.current.isEmpty()) {
            table = null;
            return;
        }
        pr.scholars.put(c.getUUID(), level.getGameTime());
        if (ticks % 30 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, table.getX() + 0.5, table.getY() + 1.3, table.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.6);
            c.markWorked();
        }
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }
}
