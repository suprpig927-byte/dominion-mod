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
 * wheat field, a tree, four citizens and a zombie), then logs what happened two minutes later.
 * Used by the automated server test; harmless to run in a creative test world.
 */
public final class SelfTest {
    private SelfTest() {}

    private static long checkAt = -1;
    private static BlockPos center;
    private static final List<UUID> citizens = new ArrayList<>();
    private static UUID zombie;
    private static UUID musketTarget;
    private static BlockPos orcCenter, townCenter, castleCenter;

    private static BlockPos surfaceAt(ServerLevel level, BlockPos p) {
        return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1, p.getZ());
    }
    private static UUID kingdom;
    private static com.dominionmod.dominion.build.Site cottage;

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
        // a second chest full of building materials for the cottage
        BlockPos chest2 = center.offset(-2, 0, 0);
        level.setBlock(chest2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest2) instanceof ChestBlockEntity be) {
            ItemStack[] mats = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 64),
                    new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.GLASS_PANE, 8), new ItemStack(Items.OAK_DOOR, 2),
                    new ItemStack(Items.BED.pick(net.minecraft.world.item.DyeColor.WHITE)),
                    new ItemStack(Items.BED.pick(net.minecraft.world.item.DyeColor.RED)), new ItemStack(Items.CHEST, 2),
                    new ItemStack(Items.CRAFTING_TABLE, 1), new ItemStack(Items.LANTERN, 2), new ItemStack(Items.OAK_SLAB, 32)};
            for (int i = 0; i < mats.length; i++) be.setItem(i, mats[i]);
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
        spawn(level, k, center.offset(4, 0, -6), new ItemStack(ModContent.BUILDERS_HAMMER.get()), null);

        cottage = new com.dominionmod.dominion.build.Site(UUID.randomUUID(), k.id, "cottage", KingdomData.dim(level), center.offset(10, -1, -10),
                center.offset(10, -1, -10), 0, List.of(), 0);
        cottage.tier = 1;
        d.addSite(cottage);
        var st = Storehouse.containers(level, k);
        ItemStack white = new ItemStack(Items.BED.pick(net.minecraft.world.item.DyeColor.WHITE));
        log("bed check: white beds in store=" + Storehouse.count(st, x -> x.is(white.getItem())) + " any beds=" + Storehouse.count(st, x -> x.is(net.minecraft.tags.ItemTags.BEDS))
                + " satisfies=" + com.dominionmod.dominion.build.Plans.satisfies(Items.BED.pick(net.minecraft.world.item.DyeColor.BLUE), white)
                + " whiteIsBed=" + white.is(net.minecraft.tags.ItemTags.BEDS));
        for (String w : new String[]{"stone_wall", "palisade", "watchtower", "barracks", "farm", "storehouse", "market_stall", "wall_gate", "library", "smithy", "university", "cottage",
                "longhouse", "farmhouse", "townhouse", "castle", "cottage_2", "longhouse_2", "farmhouse_2", "townhouse_2", "orc_hut", "orc_totem"}) {
            BlockPos a = center.offset(-15, -1, -15), b = center.offset(-5, -1, -9);
            var cols = com.dominionmod.dominion.build.Plans.line(a, b);
            var tmp = new com.dominionmod.dominion.build.Site(UUID.randomUUID(), k.id, w, KingdomData.dim(level), a, b, 1, com.dominionmod.dominion.build.Plans.groundHeights(level, cols), 0);
            tmp.tier = 1;
            log(w + " plan: " + com.dominionmod.dominion.build.Plans.steps(level, tmp, k).size() + " steps, line " + cols.size()
                    + ", materials " + com.dominionmod.dominion.build.Plans.materials(com.dominionmod.dominion.build.Structures.get(w), 1));
            com.dominionmod.dominion.build.Plans.forget(tmp.id);
        }
        log("cottage steps=" + com.dominionmod.dominion.build.Plans.steps(level, cottage, k).size());

        Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (z != null) {
            z.snapTo(center.getX() + 0.5, center.getY(), center.getZ() - 12.5, 0, 0);
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            zombie = z.getUUID();
        }
        // A Musketeer on the far side, with its own zombie to shoot.
        spawn(level, k, center.offset(16, 0, 4), new ItemStack(ModContent.MUSKET.get()), null);
        Zombie z2 = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (z2 != null) {
            z2.snapTo(center.getX() + 16.5, center.getY(), center.getZ() - 12.5, 0, 0);
            z2.setPersistenceRequired();
            level.addFreshEntity(z2);
            musketTarget = z2.getUUID();
        }
        StringBuilder deco = new StringBuilder();
        for (String[] layer : com.dominionmod.dominion.build.Structures.get("cottage").layers())
            for (String row : layer) for (char ch : row.toCharArray()) if ("1234vdexz[]wG".indexOf(ch) >= 0) deco.append(ch);
        log("cottage decorations: " + deco);
        // The wild: an orc village, a wild town and a wild kingdom with a castle, far off.
        try {
            var rs = net.minecraft.util.RandomSource.create(42);
            orcCenter = surfaceAt(level, center.offset(0, 0, -160));
            com.dominionmod.dominion.kingdom.Settlements.enqueue(level, com.dominionmod.dominion.kingdom.Settlements.Type.ORC_VILLAGE, orcCenter, rs);
            townCenter = surfaceAt(level, center.offset(170, 0, 0));
            com.dominionmod.dominion.kingdom.Settlements.enqueue(level, com.dominionmod.dominion.kingdom.Settlements.Type.WILD_TOWN, townCenter, rs);
            castleCenter = surfaceAt(level, center.offset(-220, 0, 0));
            com.dominionmod.dominion.kingdom.Settlements.enqueue(level, com.dominionmod.dominion.kingdom.Settlements.Type.WILD_KINGDOM, castleCenter, rs);
            log("wild settlements queued");
        } catch (RuntimeException e) {
            log("wild generation FAILED: " + e);
            DominionMod.LOGGER.error("wild", e);
        }
        // The new animals and a spell.
        for (var type : List.of(ModContent.DEER.get(), ModContent.CENTAUR.get(), ModContent.CLYDESDALE.get(), ModContent.MUSTANG.get(), ModContent.SHETLAND_PONY.get())) {
            var m = type.create(level, EntitySpawnReason.COMMAND);
            if (m == null) continue;
            m.snapTo(center.getX() - 12.5, center.getY() + 1, center.getZ() + 12.5, 0, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            level.addFreshEntity(m);
            log("spawned " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type) + " hp=" + m.getMaxHealth()
                    + " scale=" + m.getScale() + (m instanceof net.minecraft.world.entity.animal.equine.Horse h ? " coat=" + h.getVariant() + "/" + h.getMarkings() : ""));
        }
        checkAt = level.getGameTime() + 3000;
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
        var mz = musketTarget == null ? null : level.getEntity(musketTarget);
        log("musket target " + (mz == null || !mz.isAlive() ? "dead" : "alive hp=" + ((net.minecraft.world.entity.LivingEntity) mz).getHealth()));
        log("beds now: " + (k == null ? -1 : Realm.beds(level, k)));
        try {
            int wild = 0;
            for (Kingdom wk : d.all()) if (wk.wild) wild++;
            log("wild: orc villages=" + d.orcVillages.size() + " wild kingdoms=" + wild);
            if (orcCenter != null) {
                var orcs = level.getEntitiesOfClass(com.dominionmod.dominion.entity.wild.Orc.class, new net.minecraft.world.phys.AABB(orcCenter).inflate(40));
                log("orcs still home=" + orcs.size() + " chief=" + orcs.stream().anyMatch(com.dominionmod.dominion.entity.wild.Orc::isChief));
            }
            Kingdom found = null;
            for (Kingdom wk : d.all()) if (wk.wild && wk.name.startsWith("Kingdom of")) found = wk;
            final Kingdom wildK = found;
            if (wildK != null && k != null && wildK.hall.isPresent()) {
                int defenders = com.dominionmod.dominion.kingdom.Settlements.defenders(level, wildK, wildK.hall.get());
                int buildings = d.progress(wildK).buildings.size();
                int claimsBefore = d.claimCount(k);
                for (Citizen c : level.getEntitiesOfClass(Citizen.class, new net.minecraft.world.phys.AABB(wildK.hall.get()).inflate(64),
                        c -> c.sameKingdom(wildK.id) && c.job().soldier)) c.discard();
                com.dominionmod.dominion.kingdom.Settlements.conquer(level, wildK, k);
                log("conquest: " + wildK.name + " defenders=" + defenders + " buildings=" + buildings + " claims " + claimsBefore + " -> " + d.claimCount(k)
                        + " castle hall=" + level.getBlockState(wildK.hall.get()).getBlock());
            } else log("conquest: no wild kingdom found");
            Citizen wiz = null;
            for (UUID id : citizens) if (level.getEntity(id) instanceof Citizen c) wiz = c;
            if (wiz != null) {
                wiz.train(com.dominionmod.dominion.entity.Job.WIZARD);
                boolean ok = com.dominionmod.dominion.magic.Spells.cast(level, wiz, com.dominionmod.dominion.magic.Spell.FIREBALL,
                        new net.minecraft.world.phys.Vec3(1, 0, 0), null);
                boolean heal = com.dominionmod.dominion.magic.Spells.cast(level, wiz, com.dominionmod.dominion.magic.Spell.HEAL,
                        new net.minecraft.world.phys.Vec3(1, 0, 0), null);
                log("wizard " + wiz.job() + " holds " + wiz.getMainHandItem().getItem() + " fireball=" + ok + " heal=" + heal);
                java.util.List<BlockPos> route = List.of(center.offset(-6, 0, -6), center.offset(6, 0, -6), center.offset(6, 0, 6));
                wiz.train(com.dominionmod.dominion.entity.Job.BARBARIAN);
                wiz.patrol(route);
                log("patrol order=" + wiz.order() + " waypoints=" + wiz.patrolRoute().size());
            }
            ItemStack pouch = new ItemStack(ModContent.AMMO_POUCH.get());
            com.dominionmod.dominion.item.AmmoPouchItem.setCount(pouch, 40);
            log("pouch holds " + com.dominionmod.dominion.item.AmmoPouchItem.count(pouch));
        } catch (RuntimeException e) {
            log("wild check FAILED: " + e);
            DominionMod.LOGGER.error("wild check", e);
        }
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
            log("beds assigned=" + k.beds.size() + " names=" + k.names.values());
            var site = d.site(cottage.id);
            log("cottage " + (site == null ? "FINISHED" : "progress " + site.cursor + "/" + com.dominionmod.dominion.build.Plans.steps(level, cottage, k).size() + " status=" + site.status));
            BlockPos door = com.dominionmod.dominion.build.Plans.cell(com.dominionmod.dominion.build.Structures.get("cottage"), cottage.origin, 0, 3, 1, 6);
            log("cottage door block=" + level.getBlockState(door) + " above=" + level.getBlockState(door.above()));
            if (citizens.size() >= 2 && level.getEntity(citizens.get(0)) instanceof Citizen a && level.getEntity(citizens.get(1)) instanceof Citizen b) {
                BlockPos bedA = k.bedOf(a.getUUID()), bedB = k.bedOf(b.getUUID());
                if (bedB != null) {
                    log("swap: " + Realm.assignBed(level, k, a, bedB).getString());
                    log("after swap A=" + k.bedOf(a.getUUID()) + " (was " + bedA + ") B=" + k.bedOf(b.getUUID()) + " (was " + bedB + ")");
                }
            }
            log("influence=" + k.influence + " daily=" + Realm.dailyInfluence(k) + " claims=" + d.claimCount(k) + " cost=" + Realm.claimCost(d, k));
        }
        log("DONE");
    }
}
