package com.dominionmod.dominion.debug;

import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Site;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Flags;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Debug: builds a small finished kingdom in front of a player, for screenshots. Used by the
 * automated client test; never runs in normal play.
 */
public final class Showcase {
    private Showcase() {}

    public static final String FLAG = "14;minecraft:triangle_bottom#4,minecraft:straight_cross#15,minecraft:circle#4,minecraft:border#15";

    public static void run(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doDaylightCycle false");
        BlockPos c = p.blockPosition();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        BlockPos center = new BlockPos(c.getX(), y, c.getZ());
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-34, -1, -62), center.offset(34, 20, 10))) {
            int dy = q.getY() - center.getY();
            level.setBlock(q, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-34, -4, -62), center.offset(34, -2, 10)))
            if (level.getBlockState(q).canBeReplaced()) level.setBlock(q, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);

        KingdomData d = Realm.data(level);
        Kingdom old = d.byPlayer(p.getUUID());
        if (old != null) d.remove(old);
        Kingdom k = new Kingdom("Showcase", p.getUUID(), 14, 4, Flags.toBanner(FLAG, level.registryAccess()));
        BlockPos hall = center.offset(0, 0, -6);
        k.hall = Optional.of(hall);
        k.hallDim = KingdomData.dim(level);
        k.influence = 50;
        d.add(k);
        ChunkPos hc = ChunkPos.containing(hall);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) d.claim(level, new ChunkPos(hc.x() + dx, hc.z() + dz), k);
        level.setBlock(hall, ModContent.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
        Realm.raiseFlag(level, hall, k, p);

        // One cottage in every tier, then the other buildings behind.
        for (int t = 0; t < 5; t++) instant(level, k, "cottage", center.offset(-24 + t * 12, -1, -20), 0, t);
        instant(level, k, "storehouse", center.offset(-26, -1, -38), 0, 2);
        instant(level, k, "library", center.offset(-13, -1, -38), 0, 1);
        instant(level, k, "smithy", center.offset(-2, -1, -38), 0, 3);
        instant(level, k, "barracks", center.offset(10, -1, -38), 0, 4);
        instant(level, k, "university", center.offset(26, -1, -40), 0, 3);
        instant(level, k, "watchtower", center.offset(-26, -1, -54), 0, 2);
        instant(level, k, "farm", center.offset(-12, -1, -54), 0, 1);
        instant(level, k, "market_stall", center.offset(0, -1, -54), 0, 1);

        // One of every job, in a row in front of the player.
        Object[][] row = {
                {null, null},
                {new ItemStack(Items.IRON_HOE), null},
                {new ItemStack(Items.IRON_AXE), null},
                {new ItemStack(ModContent.BUILDERS_HAMMER.get()), null},
                {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE)},
                {new ItemStack(Items.BOW), null},
        };
        String[] types = {"plains", "desert", "taiga", "savanna", "snow", "jungle"};
        for (int i = 0; i < row.length; i++) {
            Citizen cit = ModContent.CITIZEN.get().create(level, EntitySpawnReason.COMMAND);
            if (cit == null) continue;
            cit.snapTo(center.getX() - 5 + i * 2 + 0.5, center.getY(), center.getZ() - 3.5, 0, 0);
            cit.setYHeadRot(0);
            cit.joinKingdom(k, level);
            cit.setVillagerTypeForDebug(types[i]);
            if (row[i][0] != null) cit.setItemSlot(EquipmentSlot.MAINHAND, (ItemStack) row[i][0]);
            if (row[i][1] != null) cit.setItemSlot(EquipmentSlot.CHEST, (ItemStack) row[i][1]);
            cit.setNoAi(true);
            level.addFreshEntity(cit);
        }
        // Research done so far (and one in progress), so every tier and class is unlocked.
        var pr = d.progress(k);
        pr.researched.addAll(List.of("timber", "stone", "brick", "blacksmithing", "university"));
        pr.current = "royal";
        pr.work = 9600;
        // Second row: the new jobs and classes, and a child.
        String[] row2 = {"SCHOLAR", "BLACKSMITH", "WIZARD", "BARBARIAN", "MONK", "ENGINEER", "CHILD"};
        for (int i = 0; i < row2.length; i++) {
            Citizen cit = ModContent.CITIZEN.get().create(level, EntitySpawnReason.COMMAND);
            if (cit == null) continue;
            cit.snapTo(center.getX() - 6 + i * 2 + 0.5, center.getY(), center.getZ() - 10.5, 0, 0);
            cit.joinKingdom(k, level);
            cit.setVillagerTypeForDebug(types[i % types.length]);
            switch (row2[i]) {
                case "SCHOLAR" -> cit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOOK));
                case "BLACKSMITH" -> cit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModContent.BLACKSMITH_TONGS.get()));
                case "BARBARIAN" -> {
                    cit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    cit.train(com.dominionmod.dominion.entity.Job.BARBARIAN);
                }
                case "CHILD" -> cit.makeChild();
                default -> cit.train(com.dominionmod.dominion.entity.Job.valueOf(row2[i]));
            }
            cit.setNoAi(true);
            level.addFreshEntity(cit);
        }
        p.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 3.5, java.util.Set.of(), 180f, 10f, true);
    }

    /** Builds a structure immediately (no builders, no materials). */
    public static void instant(ServerLevel level, Kingdom k, String structure, BlockPos ground, int rot, int tier) {
        Site site = new Site(UUID.randomUUID(), k.id, structure, KingdomData.dim(level), ground, ground, rot, List.of(), 0);
        site.tier = tier;
        for (Plans.Step s : Plans.steps(level, site, k)) {
            if (s.isClear()) {
                level.setBlock(s.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                continue;
            }
            level.setBlock(s.pos(), s.state(), Block.UPDATE_ALL);
            if (s.companion() != null) {
                level.setBlock(s.companionPos(), s.companion(), Block.UPDATE_ALL);
                level.setBlock(s.pos(), s.state(), Block.UPDATE_ALL);
            }
            if (s.flag() && level.getBlockEntity(s.pos()) != null) level.getBlockEntity(s.pos()).applyComponentsFromItemStack(k.flag);
        }
        // Let fences and panes connect.
        for (Plans.Step s : Plans.steps(level, site, k)) {
            if (s.isClear() || s.companion() != null) continue;
            var st = level.getBlockState(s.pos());
            var upd = Block.updateFromNeighbourShapes(st, level, s.pos());
            if (!upd.isAir() && upd != st) level.setBlock(s.pos(), upd, Block.UPDATE_CLIENTS);
        }
        Plans.forget(site.id);
        if (structure.equals("storehouse")) k.storehouses.add(ground.above().asLong());
        com.dominionmod.dominion.build.Buildings.finished(level, k, site);
    }
}
