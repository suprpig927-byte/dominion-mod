package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/** Farmers harvest ripe crops around their work spot, replant them, and sow any empty farmland. */
public class FarmGoal extends Goal {
    public static final int RADIUS = 10;
    private final Citizen c;
    private BlockPos target;
    private boolean planting;
    private int cooldown, ticks, work;

    public FarmGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (c.job() != Job.FARMER || c.order() != Order.DUTY || c.getTarget() != null || !c.level().isBrightOutside()) return false;
        if (c.inventoryFull()) return false;
        if (--cooldown > 0) return false;
        cooldown = 40;
        return find();
    }

    private boolean find() {
        BlockPos post = c.post();
        BlockPos best = null;
        boolean bestPlant = false;
        double bestD = Double.MAX_VALUE;
        boolean haveSeed = seedSlot() >= 0;
        for (BlockPos p : BlockPos.betweenClosed(post.offset(-RADIUS, -2, -RADIUS), post.offset(RADIUS, 2, RADIUS))) {
            BlockState s = c.level().getBlockState(p);
            boolean harvest = s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
            boolean plant = !harvest && haveSeed && s.is(Blocks.FARMLAND) && c.level().getBlockState(p.above()).isAir();
            if (!harvest && !plant) continue;
            double d = c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) + (plant ? 16 : 0); // harvesting comes first
            if (d < bestD) {
                bestD = d;
                best = plant ? p.above().immutable() : p.immutable();
                bestPlant = plant;
            }
        }
        target = best;
        if (best != null) c.markWorked();
        planting = bestPlant;
        return best != null;
    }

    private int seedSlot() {
        var inv = c.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (Citizen.isPlantable(inv.getItem(i))) return i;
        return -1;
    }

    @Override
    public void start() {
        ticks = 0;
        work = 0;
        moveTo();
    }

    private void moveTo() {
        c.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.5);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < 400 && c.getTarget() == null && c.job() == Job.FARMER && c.order() == Order.DUTY;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) return;
        ticks++;
        c.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.3, target.getZ() + 0.5);
        if (c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 2.6 * 2.6) {
            if (ticks % 20 == 0) moveTo();
            return;
        }
        c.getNavigation().stop();
        if (++work < 12) return;
        if (c.level() instanceof ServerLevel level) {
            if (planting) plant(level);
            else harvest(level);
        }
        c.swingForAttack(InteractionHand.MAIN_HAND);
        target = null;
        cooldown = 5;
    }

    private void harvest(ServerLevel level) {
        BlockState s = level.getBlockState(target);
        if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) return;
        boolean replanted = false;
        for (ItemStack drop : Block.getDrops(s, level, target, null)) {
            if (!replanted && drop.is(crop.asItem())) {
                drop.shrink(1); // one seed goes back in the ground
                replanted = true;
            }
            if (!drop.isEmpty()) c.stash(level, drop);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(s));
        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL);
    }

    private void plant(ServerLevel level) {
        int slot = seedSlot();
        if (slot < 0 || !level.getBlockState(target).isAir() || !level.getBlockState(target.below()).is(Blocks.FARMLAND)) return;
        ItemStack seed = c.getInventory().getItem(slot);
        if (!(seed.getItem() instanceof BlockItem bi)) return;
        BlockState crop = bi.getBlock().defaultBlockState();
        if (!crop.canSurvive(level, target)) return;
        level.setBlock(target, crop, Block.UPDATE_ALL);
        seed.shrink(1);
        c.getInventory().setChanged();
    }

    @Override
    public void stop() {
        target = null;
        c.getNavigation().stop();
    }
}
