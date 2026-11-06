package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/** Lumberjacks fell whole trees near their work spot, log by log, and plant a sapling in the stump's place. */
public class LumberGoal extends Goal {
    public static final int RADIUS = 14;
    private static final int MAX_LOGS = 96;
    private final Citizen c;
    private BlockPos base, stand;
    private final List<BlockPos> logs = new ArrayList<>();
    private Block logType = Blocks.OAK_LOG;
    private int cooldown, ticks, chop;

    public LumberGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (c.job() != Job.LUMBERJACK || c.order() != Order.DUTY || c.getTarget() != null || !c.level().isBrightOutside()) return false;
        if (c.inventoryFull()) return false;
        if (--cooldown > 0) return false;
        cooldown = 60;
        return find();
    }

    private boolean find() {
        Level level = c.level();
        BlockPos post = c.post();
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(post.offset(-RADIUS, -4, -RADIUS), post.offset(RADIUS, 6, RADIUS))) {
            if (level.getBlockState(p).is(BlockTags.LOGS) && level.getBlockState(p.below()).is(BlockTags.DIRT)) bases.add(p.immutable());
        }
        bases.sort(Comparator.comparingDouble(p -> c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p))));
        for (BlockPos b : bases) {
            if (collectTree(level, b)) {
                base = b;
        if (b != null) c.markWorked();
                logType = level.getBlockState(b).getBlock();
                return true;
            }
        }
        return false;
    }

    /** Flood-fills the logs of a tree. Only counts as a tree if leaves touch it (so they don't chop your house). */
    private boolean collectTree(Level level, BlockPos start) {
        logs.clear();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        boolean leaves = false;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = queue.poll();
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (n.getY() < start.getY() || !seen.add(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(BlockTags.LOGS)) queue.add(n);
                else if (s.is(BlockTags.LEAVES)) leaves = true;
            }
        }
        // Chop from the top down so the trunk doesn't float.
        logs.sort(Comparator.comparingInt((BlockPos b) -> b.getY()).reversed());
        return leaves && !logs.isEmpty();
    }

    @Override
    public void start() {
        ticks = 0;
        chop = 0;
        stand = Spots.standNear(c.level(), base, c);
        c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.5);
    }

    @Override
    public boolean canContinueToUse() {
        return base != null && !logs.isEmpty() && ticks < 1200 && c.getTarget() == null && c.job() == Job.LUMBERJACK
                && c.order() == Order.DUTY && !c.inventoryFull();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (base == null || logs.isEmpty()) return;
        ticks++;
        BlockPos next = logs.get(0);
        c.getLookControl().setLookAt(next.getX() + 0.5, Math.min(next.getY(), base.getY() + 2) + 0.5, next.getZ() + 0.5);
        if (c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(base)) > 3.2 * 3.2) {
            if (ticks % 20 == 0) c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.5);
            return;
        }
        c.getNavigation().stop();
        if (++chop % 8 != 0) return;
        c.swingForAttack(InteractionHand.MAIN_HAND);
        if (!(c.level() instanceof ServerLevel level)) return;
        BlockPos p = logs.remove(0);
        BlockState s = level.getBlockState(p);
        if (s.is(BlockTags.LOGS)) {
            for (ItemStack drop : Block.getDrops(s, level, p, null)) c.stash(level, drop);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (logs.isEmpty()) replant(level);
    }

    private void replant(ServerLevel level) {
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.below()).is(BlockTags.DIRT)) return;
        Block sapling = saplingFor(logType);
        if (sapling == null) return;
        // Use one of their saplings if they have a matching one; otherwise plant one anyway.
        var inv = c.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(sapling.asItem())) {
                inv.getItem(i).shrink(1);
                break;
            }
        }
        BlockState state = sapling.defaultBlockState();
        if (state.canSurvive(level, base)) level.setBlock(base, state, Block.UPDATE_ALL);
    }

    private static Block saplingFor(Block log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log);
        String path = id.getPath();
        if (path.startsWith("stripped_")) path = path.substring(9);
        String sap = path.equals("mangrove_log") ? "mangrove_propagule" : path.replace("_log", "_sapling");
        Identifier sid = Identifier.fromNamespaceAndPath(id.getNamespace(), sap);
        Block b = BuiltInRegistries.BLOCK.getValue(sid);
        return b == null || b == Blocks.AIR ? null : b;
    }

    @Override
    public void stop() {
        base = null;
        logs.clear();
        c.getNavigation().stop();
    }
}
