package com.dominionmod.dominion.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A kingdom's storehouse is simply every chest and barrel standing near its Town Hall.
 * Workers fill it, citizens eat from it, taxes go in and wages come out.
 */
public final class Storehouse {
    private Storehouse() {}

    public static final int RADIUS = 6;
    public static final int HEIGHT = 3;

    public static List<Container> containers(ServerLevel level, Kingdom k) {
        List<Container> out = new ArrayList<>();
        if (k.hall.isEmpty() || !KingdomData.dim(level).equals(k.hallDim)) return out;
        BlockPos hall = k.hall.get();
        for (BlockPos p : BlockPos.betweenClosed(hall.offset(-RADIUS, -HEIGHT, -RADIUS), hall.offset(RADIUS, HEIGHT, RADIUS))) {
            if (!level.isLoaded(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c && isStorage(be)) out.add(c);
        }
        return out;
    }

    private static boolean isStorage(BlockEntity be) {
        return be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
    }

    /** Puts as much of the stack as fits; returns what's left over. */
    public static ItemStack insert(List<Container> containers, ItemStack stack) {
        ItemStack left = stack.copy();
        for (Container c : containers) {
            // First top up matching stacks, then use empty slots.
            for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
                for (int i = 0; i < c.getContainerSize() && !left.isEmpty(); i++) {
                    ItemStack in = c.getItem(i);
                    if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, left)) {
                        int room = Math.min(in.getMaxStackSize(), c.getMaxStackSize()) - in.getCount();
                        int move = Math.min(room, left.getCount());
                        if (move > 0) {
                            in.grow(move);
                            left.shrink(move);
                            c.setChanged();
                        }
                    } else if (pass == 1 && in.isEmpty()) {
                        int move = Math.min(left.getCount(), Math.min(left.getMaxStackSize(), c.getMaxStackSize()));
                        c.setItem(i, left.split(move));
                        c.setChanged();
                    }
                }
            }
            if (left.isEmpty()) break;
        }
        return left;
    }

    public static ItemStack insert(ServerLevel level, Kingdom k, ItemStack stack) {
        return insert(containers(level, k), stack);
    }

    public static int count(List<Container> containers, Predicate<ItemStack> filter) {
        int n = 0;
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack in = c.getItem(i);
                if (!in.isEmpty() && filter.test(in)) n += in.getCount();
            }
        }
        return n;
    }

    /** Removes up to {@code amount} matching items; returns one stack of what was taken (first match's type). */
    public static ItemStack take(List<Container> containers, Predicate<ItemStack> filter, int amount) {
        ItemStack taken = ItemStack.EMPTY;
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
                ItemStack in = c.getItem(i);
                if (in.isEmpty() || !filter.test(in)) continue;
                if (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, in)) continue;
                int move = Math.min(amount, in.getCount());
                if (taken.isEmpty()) taken = in.copyWithCount(move);
                else taken.grow(move);
                in.shrink(move);
                amount -= move;
                c.setChanged();
            }
        }
        return taken;
    }
}
