package com.dominionmod.dominion.debug;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * /dominion admin selftest: builds a tiny kingdom far from spawn (a hall, a storehouse, beds, a
 * wheat field, a tree, four citizens and a zombie), then logs what happened 30 seconds later.
 * Used by the automated server test; harmless to run in a creative test world.
 */
public final class SelfTest {
    private SelfTest() {}

    private static long checkAt = -1;
    private static BlockPos center;
    private static final List<UUID> citizens = new ArrayList<>();
    private static UUID zombie;
    private static UUID kingdom;

    private static void log(String s) {
        DominionMod.LOGGER.info("SELFTEST " + s);
    }

    public static void start(ServerLevel level) {
        int bx = 4000, bz = 4000;
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.setChunkForced((bx >> 4) + cx, (bz >> 4) + cz, true);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        center = new BlockPos(bx, y, bz);
        // flat grass platform, air above
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -1, -20), center.offset(20, 12, 20))) {
            int dy = p.getY() - center.getY();
            level.setBlock(p, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -3, -20), center.offset(20, -2, 20))) level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);

        KingdomData d = Realm.data(level);
        Kingdom old = d.byName("Testland");
        if (old != null) d.remove(old);
        Kingdom k = new Kingdom("Testland", UUID.randomUUID(), 11, 4, ItemStack.EMPTY);
        k.hall = Optional.of(center.immutable());
        k.hallDim = KingdomData.dim(level);
        k.influence = 100;
        d.add(k);
        kingdom = k.id;
        ChunkPos cc = ChunkPos.containing(center);
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) d.claim(level, new ChunkPos(cc.x() + cx, cc.z() + cz), k);
        level.setBlock(center, ModContent.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
        Realm.raiseFlag(level, center, k, null);
        log("flag raised: " + level.getBlockState(center.above()));

        // storehouse with food and coin
        BlockPos chest = center.offset(2, 0, 0);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
            be.setItem(0, new ItemStack(Items.BREAD, 32));
            be.setItem(1, new ItemStack(ModContent.GOLD_COIN.get(), 20));
        }
        // beds
        for (int i = 0; i < 6; i++) {
            BlockPos foot = center.offset(-6, 0, -6 + i * 2);
            Block bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED);
            level.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
            level.setBlock(foot.east(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
        }
        // wheat field around water, all ripe
        BlockPos field = center.offset(8, -1, 8);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = field.offset(dx, 0, dz);
            if (dx == 0 && dz == 0) {
                level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            level.setBlock(p, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(p.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7), Block.UPDATE_ALL);
        }
        // an oak tree
        BlockPos tree = center.offset(-8, 0, 8);
        for (int i = 0; i < 5; i++) level.setBlock(tree.above(i), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos p : BlockPos.betweenClosed(tree.offset(-2, 3, -2), tree.offset(2, 5, 2))) {
            if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_ALL);
        }
        log("beds counted: " + Realm.beds(level, k));

        citizens.clear();
        spawn(level, k, field.above().offset(-3, 0, 0), new ItemStack(Items.IRON_HOE), null);
        spawn(level, k, tree.offset(2, 0, 0), new ItemStack(Items.IRON_AXE), null);
        spawn(level, k, center.offset(0, 0, -4), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE));
        spawn(level, k, center.offset(2, 0, -4), new ItemStack(Items.BOW), null);

        Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (z != null) {
            z.snapTo(center.getX() + 0.5, center.getY(), center.getZ() - 12.5, 0, 0);
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            zombie = z.getUUID();
        }
        checkAt = level.getGameTime() + 600;
        log("started at " + center + ", citizens: " + citizens.size() + ", kingdom citizens: " + k.citizens);
    }

    private static void spawn(ServerLevel level, Kingdom k, BlockPos at, ItemStack hand, ItemStack chest) {
        Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        Citizen c = ModContent.CITIZEN.get().create(level, EntitySpawnReason.CONVERSION);
        if (c == null) {
            log("FAIL could not create citizen");
            return;
        }
        c.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        c.joinKingdom(k, level);
        c.setItemSlot(EquipmentSlot.MAINHAND, hand);
        if (chest != null) c.setItemSlot(EquipmentSlot.CHEST, chest);
        level.addFreshEntity(c);
        citizens.add(c.getUUID());
        if (v != null) v.discard();
    }

    public static void tick(ServerLevel level) {
        if (checkAt < 0 || level.getGameTime() < checkAt) return;
        checkAt = -1;
        KingdomData d = Realm.data(level);
        Kingdom k = d.byId(kingdom);
        BlockPos field = center.offset(8, 0, 8);
        int ripe = 0, young = 0;
        for (BlockPos p : BlockPos.betweenClosed(field.offset(-2, 0, -2), field.offset(2, 0, 2))) {
            var s = level.getBlockState(p);
            if (s.getBlock() instanceof CropBlock crop) {
                if (crop.isMaxAge(s)) ripe++;
                else young++;
            }
        }
        log("beds now: " + (k == null ? -1 : Realm.beds(level, k)));
        log("wheat ripe=" + ripe + " replanted=" + young);
        BlockPos tree = center.offset(-8, 0, 8);
        int logs = 0;
        for (int i = 0; i < 5; i++) if (level.getBlockState(tree.above(i)).is(Blocks.OAK_LOG)) logs++;
        log("tree logs left=" + logs + " base=" + level.getBlockState(tree));
        for (UUID id : citizens) {
            if (level.getEntity(id) instanceof Citizen c) {
                log("citizen " + c.baseName() + " job=" + c.job() + " pos=" + c.blockPosition() + " hp=" + c.getHealth()
                        + " carrying=" + c.getInventory().getItems().stream().filter(s -> !s.isEmpty()).map(s -> s.getCount() + "x" + s.getItem()).toList());
            } else {
                log("citizen " + id + " is gone");
            }
        }
        log("zombie alive=" + (level.getEntity(zombie) != null && level.getEntity(zombie).isAlive()));
        if (k != null) {
            var store = Storehouse.containers(level, k);
            log("storehouse containers=" + store.size() + " bread=" + Storehouse.count(store, s -> s.is(Items.BREAD))
                    + " coins=" + Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get())) + " wheat=" + Storehouse.count(store, s -> s.is(Items.WHEAT))
                    + " logs=" + Storehouse.count(store, s -> s.is(Items.OAK_LOG)) + " food=" + Storehouse.count(store, s -> s.has(DataComponents.FOOD)));
            for (UUID id : citizens) if (level.getEntity(id) instanceof Citizen c) c.debugUpkeep();
            log("after one upkeep: bread=" + Storehouse.count(store, s -> s.is(Items.BREAD)) + " coins="
                    + Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get())));
            log("influence=" + k.influence + " daily=" + Realm.dailyInfluence(k) + " claims=" + d.claimCount(k) + " cost=" + Realm.claimCost(d, k));
        }
        log("DONE");
    }
}
