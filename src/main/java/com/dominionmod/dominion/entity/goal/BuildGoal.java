package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Site;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builders work through a construction site one block at a time: fetch materials from the
 * storehouse, walk over, clear what's in the way and place each block.
 */
public class BuildGoal extends Goal {
    private static final double REACH = 4.5;
    private static final int LOOKAHEAD = 64;
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private enum Phase { FETCH, WORK }

    private final Citizen c;
    private Site site;
    private Phase phase;
    private BlockPos walkTo;
    private int cooldown, ticks, placeTimer, stuck;

    public BuildGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (c.job() != Job.BUILDER || c.order() != Order.DUTY || c.getTarget() != null || !c.level().isBrightOutside()) return false;
        if (c.inventoryFull() || !(c.level() instanceof ServerLevel level)) return false;
        if (--cooldown > 0) return false;
        cooldown = 40;
        Kingdom k = c.kingdom();
        if (k == null) return false;
        KingdomData d = Realm.data(level);
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Site s : d.sites(k)) {
            if (!s.dim.equals(KingdomData.dim(level))) continue;
            double dist = c.distanceToSqr(Vec3.atCenterOf(s.origin));
            if (dist < bestD && dist < 192 * 192) {
                bestD = dist;
                best = s;
            }
        }
        site = best;
        return site != null;
    }

    @Override
    public void start() {
        ticks = 0;
        stuck = 0;
        phase = Phase.WORK;
        walkTo = null;
        c.markWorked();
    }

    @Override
    public boolean canContinueToUse() {
        return site != null && c.job() == Job.BUILDER && c.order() == Order.DUTY && c.getTarget() == null && !c.inventoryFull()
                && c.level().isBrightOutside() && ticks < 6000;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        site = null;
        c.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (site == null || !(c.level() instanceof ServerLevel level)) return;
        ticks++;
        KingdomData d = Realm.data(level);
        Kingdom k = c.kingdom();
        if (k == null || d.site(site.id) == null) {
            site = null;
            return;
        }
        List<Plans.Step> steps = Plans.steps(level, site, k);
        skipDone(level, steps);
        if (site.cursor >= steps.size()) {
            finish(level, d, k);
            return;
        }
        Plans.Step next = steps.get(site.cursor);

        if (phase == Phase.FETCH) {
            fetch(level, k, steps);
            return;
        }
        if (next.cost() != null && !hasMaterial(next.cost())) {
            phase = Phase.FETCH;
            walkTo = null;
            return;
        }
        // Walk to within reach of the next block.
        if (c.distanceToSqr(Vec3.atCenterOf(next.pos())) > REACH * REACH) {
            if (walkTo == null || ticks % 40 == 0) {
                walkTo = Spots.standNear(level, next.pos(), c);
                if (!c.getNavigation().moveTo(walkTo.getX() + 0.5, walkTo.getY(), walkTo.getZ() + 0.5, 0.55)) stuck++;
            }
            // If they can't get there, build it from where they are (they're skilled).
            if (stuck < 6 && c.distanceToSqr(Vec3.atCenterOf(next.pos())) < 9 * 9 && ticks % 200 == 199) stuck = 6;
            if (stuck < 6) return;
        }
        c.getLookControl().setLookAt(Vec3.atCenterOf(next.pos()));
        if (++placeTimer < 6) return;
        placeTimer = 0;
        stuck = 0;
        perform(level, k, next);
        site.cursor++;
        d.markChanged();
    }

    private void skipDone(ServerLevel level, List<Plans.Step> steps) {
        int guard = 0;
        while (site.cursor < steps.size() && guard++ < 64 && done(level, steps.get(site.cursor))) site.cursor++;
    }

    private static boolean done(ServerLevel level, Plans.Step s) {
        BlockState now = level.getBlockState(s.pos());
        if (s.isClear()) return now.isAir();
        return now.getBlock() == s.state().getBlock();
    }

    private void perform(ServerLevel level, Kingdom k, Plans.Step s) {
        c.swingForAttack(InteractionHand.MAIN_HAND);
        BlockState now = level.getBlockState(s.pos());
        if (now.is(com.dominionmod.dominion.registry.ModContent.TOWN_HALL.get())) return; // never knock down the Town Hall
        if (!now.isAir() && (s.isClear() || now.getBlock() != s.state().getBlock())) {
            if (now.getFluidState().isEmpty()) {
                for (ItemStack drop : Block.getDrops(now, level, s.pos(), level.getBlockEntity(s.pos()))) c.stash(level, drop);
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, s.pos(), Block.getId(now));
            }
            level.setBlock(s.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (s.isClear()) return;
        if (s.cost() != null && !useMaterial(s.cost())) return;
        BlockState state = Block.updateFromNeighbourShapes(s.state(), level, s.pos());
        level.setBlock(s.pos(), state, Block.UPDATE_ALL);
        if (s.companion() != null && s.companionPos() != null) {
            if (!level.getBlockState(s.companionPos()).isAir()) level.destroyBlock(s.companionPos(), true);
            level.setBlock(s.companionPos(), s.companion(), Block.UPDATE_ALL);
        }
        if (s.flag()) {
            var be = level.getBlockEntity(s.pos());
            if (be != null && !k.flag.isEmpty()) {
                be.applyComponentsFromItemStack(k.flag);
                be.setChanged();
                level.sendBlockUpdated(s.pos(), state, state, Block.UPDATE_ALL);
            }
        }
        var sound = state.getSoundType().getPlaceSound();
        level.playSound(null, s.pos(), sound, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    // ------------------------------------------------------------------ materials

    private boolean hasMaterial(Item cost) {
        SimpleContainer m = c.materials();
        for (int i = 0; i < m.getContainerSize(); i++) if (!m.getItem(i).isEmpty() && Plans.satisfies(cost, m.getItem(i))) return true;
        return false;
    }

    private boolean useMaterial(Item cost) {
        SimpleContainer m = c.materials();
        for (int i = 0; i < m.getContainerSize(); i++) {
            ItemStack s = m.getItem(i);
            if (!s.isEmpty() && Plans.satisfies(cost, s)) {
                s.shrink(1);
                m.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Walk to the Town Hall and load up on what the next stretch of the build needs. */
    private void fetch(ServerLevel level, Kingdom k, List<Plans.Step> steps) {
        if (k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(level))) {
            site.status = "No Town Hall to fetch materials from";
            site = null;
            cooldown = 400;
            return;
        }
        BlockPos hall = k.hall.get();
        if (c.distanceToSqr(Vec3.atCenterOf(hall)) > 4.5 * 4.5) {
            if (walkTo == null || ticks % 40 == 0) {
                walkTo = Spots.standNear(level, hall, c);
                c.getNavigation().moveTo(walkTo.getX() + 0.5, walkTo.getY(), walkTo.getZ() + 0.5, 0.6);
            }
            return;
        }
        // What does the next stretch need?
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (int i = site.cursor; i < steps.size() && i < site.cursor + LOOKAHEAD; i++) {
            Plans.Step s = steps.get(i);
            if (s.cost() != null && !done(level, s)) need.merge(s.cost(), 1, Integer::sum);
        }
        var store = Storehouse.containers(level, k);
        Item first = steps.get(site.cursor).cost();
        boolean gotFirst = false;
        for (var e : need.entrySet()) {
            int want = Math.min(e.getValue(), 64);
            ItemStack got = Storehouse.take(store, s -> Plans.satisfies(e.getKey(), s), want);
            if (got.isEmpty()) continue;
            if (e.getKey() == first) gotFirst = true;
            ItemStack left = c.materials().addItem(got);
            if (!left.isEmpty()) Storehouse.insert(store, left);
        }
        walkTo = null;
        if (!gotFirst && first != null && !hasMaterial(first)) {
            String name = new ItemStack(first).getHoverName().getString();
            site.status = "Waiting for " + name;
            long day = level.getGameTime() / Citizen.DAY;
            if (!Long.valueOf(day).equals(WARNED.get(site.id))) {
                WARNED.put(site.id, day);
                Citizen.tellKingdom(level, k, Component.literal("Builders need " + name + " for the " + site.name()
                        + ". Put some in the storehouse.").withStyle(ChatFormatting.YELLOW));
            }
            site = null;
            cooldown = 400;
            return;
        }
        site.status = "Building";
        phase = Phase.WORK;
    }

    private void finish(ServerLevel level, KingdomData d, Kingdom k) {
        d.removeSite(site);
        Citizen.tellKingdom(level, k, Component.literal("The " + site.name() + " is finished!").withStyle(ChatFormatting.GREEN));
        // Leftover materials go back to the storehouse.
        var store = Storehouse.containers(level, k);
        for (ItemStack s : c.materials().removeAllItems()) {
            ItemStack left = Storehouse.insert(store, s);
            if (!left.isEmpty()) c.stash(level, left);
        }
        site = null;
    }
}
