package com.dominionmod.dominion.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;

/** Finishing touches once furniture is in place: wall shelves get books, bottles and bowls on them. */
public final class Furnish {
    private Furnish() {}

    private static final Item[] SHELF_THINGS = {Items.BOOK, Items.BOOK, Items.WRITABLE_BOOK, Items.GLASS_BOTTLE, Items.HONEY_BOTTLE, Items.BOWL,
            Items.FLOWER_POT, Items.CANDLE, Items.APPLE, Items.BREAD, Items.PAPER, Items.CLOCK, Items.SPYGLASS, Items.AMETHYST_SHARD};

    public static void dress(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf)) return;
        if (!shelf.isEmpty()) return;
        RandomSource r = RandomSource.create(pos.asLong());
        for (int i = 0; i < shelf.getContainerSize(); i++) {
            if (r.nextInt(5) == 0) continue; // leave a gap now and then
            shelf.setItem(i, new ItemStack(SHELF_THINGS[r.nextInt(SHELF_THINGS.length)]));
        }
        shelf.setChanged();
        var st = level.getBlockState(pos);
        level.sendBlockUpdated(pos, st, st, net.minecraft.world.level.block.Block.UPDATE_ALL);
    }
}
