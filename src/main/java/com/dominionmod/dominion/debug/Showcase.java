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
        // The flat test world spawns slimes; keep them away from the camera and keep the camera alive.
        for (String cmd : new String[]{"gamerule doMobSpawning false", "gamerule spawn_mobs false", "gamerule advance_time false", "kill @e[type=minecraft:slime]"})
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        p.getAbilities().invulnerable = true;
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
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
        // (the watchtower, farm and market stall stand in the war camp to the west; see warCamp)

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
        // Third row (behind the player): soldiers with the new weapons, facing north.
        ItemStack[][] armory = {
                {new ItemStack(ModContent.LONGSWORD.get()), new ItemStack(ModContent.IRON_SHIELD.get())},
                {new ItemStack(ModContent.GREATSWORD.get()), null},
                {new ItemStack(ModContent.BATTLE_AXE.get()), null},
                {new ItemStack(ModContent.HALBERD.get()), null},
                {new ItemStack(ModContent.GLAIVE.get()), null},
                {new ItemStack(ModContent.FLAIL.get()), new ItemStack(ModContent.IRON_SHIELD.get())},
                {new ItemStack(ModContent.FLINTLOCK.get()), null},
                {new ItemStack(ModContent.MUSKET.get()), null},
                {new ItemStack(Items.IRON_SWORD), null},
        };
        for (int i = 0; i < armory.length; i++) {
            Citizen cit = ModContent.CITIZEN.get().create(level, EntitySpawnReason.COMMAND);
            if (cit == null) continue;
            cit.snapTo(center.getX() - 7 + i * 2 + 0.5, center.getY(), center.getZ() + 8.5, 90, 0);
            cit.setYHeadRot(90);
            cit.setYBodyRot(90);
            cit.joinKingdom(k, level);
            cit.setVillagerTypeForDebug(types[i % types.length]);
            cit.setItemSlot(EquipmentSlot.MAINHAND, armory[i][0]);
            if (armory[i][1] != null) cit.setItemSlot(EquipmentSlot.OFFHAND, armory[i][1]);
            cit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            cit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            cit.setNoAi(true);
            level.addFreshEntity(cit);
        }
        // The player's hotbar: every new weapon, with a musket in hand and an iron shield on the arm.
        var inv = p.getInventory();
        ItemStack[] bar = {new ItemStack(ModContent.MUSKET.get()), new ItemStack(ModContent.FLINTLOCK.get()), new ItemStack(ModContent.LONGSWORD.get()),
                new ItemStack(ModContent.GREATSWORD.get()), new ItemStack(ModContent.BATTLE_AXE.get()), new ItemStack(ModContent.HALBERD.get()),
                pouch(), new ItemStack(ModContent.SPELL_BOOK.get()), new ItemStack(ModContent.BULLET.get(), 32)};
        for (int i = 0; i < bar.length; i++) inv.setItem(i, bar[i]);
        inv.setSelectedSlot(0);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModContent.IRON_SHIELD.get()));
        p.giveExperienceLevels(30);

        // The new house designs, and two of them with an upper storey, in a row behind the others.
        instant(level, k, "longhouse", center.offset(-26, -1, -56), 0, 1);
        instant(level, k, "farmhouse", center.offset(-12, -1, -56), 0, 2);
        instant(level, k, "townhouse_2", center.offset(1, -1, -56), 0, 3);
        instant(level, k, "cottage_2", center.offset(12, -1, -56), 0, 4);
        instant(level, k, "farmhouse_2", center.offset(26, -1, -56), 0, 1);

        // Wild animals standing still for their portraits: deer and the pony coats to the west,
        // every Clydesdale and Mustang coat to the east.
        record Sitter(net.minecraft.world.entity.EntityType<?> type, int look) {}
        var west = List.of(new Sitter(ModContent.DEER.get(), 1), new Sitter(ModContent.DEER.get(), 0), new Sitter(ModContent.DEER.get(), 2),
                new Sitter(ModContent.SHETLAND_PONY.get(), 0), new Sitter(ModContent.SHETLAND_PONY.get(), 1), new Sitter(ModContent.SHETLAND_PONY.get(), 2),
                new Sitter(ModContent.SHETLAND_PONY.get(), 3), new Sitter(ModContent.SHETLAND_PONY.get(), 4));
        var east = List.of(new Sitter(ModContent.CLYDESDALE.get(), 0), new Sitter(ModContent.CLYDESDALE.get(), 1), new Sitter(ModContent.CLYDESDALE.get(), 2),
                new Sitter(ModContent.MUSTANG.get(), 0), new Sitter(ModContent.MUSTANG.get(), 1), new Sitter(ModContent.MUSTANG.get(), 2),
                new Sitter(ModContent.MUSTANG.get(), 3), new Sitter(ModContent.MUSTANG.get(), 4));
        for (int lineup = 0; lineup < 2; lineup++) {
            var sitters = lineup == 0 ? west : east;
            for (int i = 0; i < sitters.size(); i++) {
                Sitter st = sitters.get(i);
                if (!(st.type().create(level, EntitySpawnReason.COMMAND) instanceof net.minecraft.world.entity.Mob m)) continue;
                double x = lineup == 0 ? center.getX() - 31.5 + i * 2.8 : center.getX() + 3.5 + i * 3.0;
                m.snapTo(x, center.getY(), center.getZ() + 8.5, 90, 0);
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
                if (m instanceof com.dominionmod.dominion.entity.wild.Deer deer) {
                    deer.forceStag(st.look() == 1);
                    if (st.look() == 2) deer.setAge(-24000);
                }
                if (m instanceof com.dominionmod.dominion.entity.wild.BreedHorse h)
                    h.setLook(h.breed.coats[st.look()], net.minecraft.world.entity.animal.equine.Markings.NONE);
                m.setYHeadRot(90);
                m.setYBodyRot(90);
                m.setNoAi(true);
                m.setPermanentlyInvulnerable(true);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
            }
        }
        // The centaur and orcs from the last round stand behind the deer.
        var others = List.of(ModContent.CENTAUR.get(), ModContent.ORC.get(), ModContent.ORC.get());
        for (int i = 0; i < others.size(); i++) {
            var m = others.get(i).create(level, EntitySpawnReason.COMMAND);
            if (m == null) continue;
            m.snapTo(center.getX() - 30.5 + i * 3.0, center.getY(), center.getZ() + 12.5, 90, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            if (m instanceof com.dominionmod.dominion.entity.wild.Orc orc && i == 2) orc.makeChief();
            m.setNoAi(true);
            m.setPermanentlyInvulnerable(true);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
        }

        // The three centaur looks, side by side.
        for (int i = 0; i < 3; i++) {
            var cen = ModContent.CENTAUR.get().create(level, EntitySpawnReason.COMMAND);
            if (cen == null) continue;
            cen.setUUID(new java.util.UUID(0x5EED + i * 7919L, i));
            cen.snapTo(center.getX() - 2.5 + i * 3.2, center.getY(), center.getZ() + 18.5, 90, 0);
            cen.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            cen.setYHeadRot(90);
            cen.setYBodyRot(90);
            cen.setNoAi(true);
            cen.setPermanentlyInvulnerable(true);
            cen.setPersistenceRequired();
            level.addFreshEntity(cen);
        }

        // An orc village to the east and a wild kingdom with its castle to the south.
        var rs = net.minecraft.util.RandomSource.create(7);
        int ox = center.getX() + 72, oz = center.getZ();
        com.dominionmod.dominion.kingdom.Settlements.generateNow(level, com.dominionmod.dominion.kingdom.Settlements.Type.ORC_VILLAGE,
                new BlockPos(ox, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz) - 1, oz), rs);
        int cx = center.getX(), cz = center.getZ() + 64;
        com.dominionmod.dominion.kingdom.Settlements.generateNow(level, com.dominionmod.dominion.kingdom.Settlements.Type.WILD_KINGDOM,
                new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1, cz), rs);
        for (var orc : level.getEntitiesOfClass(com.dominionmod.dominion.entity.wild.Orc.class, new net.minecraft.world.phys.AABB(new BlockPos(ox, center.getY(), oz)).inflate(40))) {
            orc.setNoAi(true);
        }
        // The orc village has grown into a stronghold with its Warlord.
        for (var v : d.orcVillages) if (!v.conquered && v.totem.distSqr(new BlockPos(ox, v.totem.getY(), oz)) < 40 * 40)
            com.dominionmod.dominion.kingdom.OrcWarbands.fastForward(level, v);
        for (var orc : level.getEntitiesOfClass(com.dominionmod.dominion.entity.wild.Orc.class, new net.minecraft.world.phys.AABB(new BlockPos(ox, center.getY(), oz)).inflate(48)))
            orc.setNoAi(true);
        warCamp(level, k, center);
        townQuarter(level, k, center);
        pets(level, p, center);
        p.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 3.5, java.util.Set.of(), 180f, 10f, true);
    }

    private static ItemStack pouch() {
        ItemStack s = new ItemStack(ModContent.AMMO_POUCH.get());
        com.dominionmod.dominion.item.AmmoPouchItem.setCount(s, 300);
        return s;
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
            com.dominionmod.dominion.build.Furnish.dress(level, s.pos());
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

    /** West of the town: stables, mounted soldiers, a supply cart, the three siege engines, a road and a Warlord. */
    private static void warCamp(ServerLevel level, Kingdom k, BlockPos center) {
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-98, -1, -26), center.offset(-36, 20, 12))) {
            int dy = q.getY() - center.getY();
            level.setBlock(q, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-98, -4, -26), center.offset(-36, -2, 12)))
            if (level.getBlockState(q).canBeReplaced()) level.setBlock(q, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        var pr = Realm.data(level).progress(k);
        pr.researched.addAll(List.of("horsemanship", "siegecraft", "gunpowder"));
        instant(level, k, "stables", center.offset(-74, -1, -22), 0, 2);
        instant(level, k, "watchtower", center.offset(-94, -1, -24), 0, 2);
        instant(level, k, "farm", center.offset(-56, -1, -24), 0, 1);
        instant(level, k, "market_stall", center.offset(-44, -1, -24), 0, 1);
        // a road along the front
        BlockPos a = center.offset(-96, -1, 7), b = center.offset(-38, -1, 7);
        var cols = Plans.line(a, b);
        Site road = new Site(UUID.randomUUID(), k.id, "road", KingdomData.dim(level), a, b, 0, Plans.groundHeights(level, cols), 0);
        road.tier = 1;
        for (Plans.Step st : Plans.steps(level, road, k)) level.setBlock(st.pos(), st.isClear() ? Blocks.AIR.defaultBlockState() : st.state(), Block.UPDATE_CLIENTS);
        Plans.forget(road.id);
        // the siege engines, facing east
        String[] engines = {"battering_ram", "trebuchet", "cannon"};
        double[] ex = {-90.5, -81.5, -72.5};
        for (int i = 0; i < 3; i++) {
            var e = com.dominionmod.dominion.entity.siege.SiegeEngine.spawnFor(level, k, engines[i].equals("battering_ram") ? "ram" : engines[i],
                    center.offset((int) ex[i], 0, 0), center.offset((int) ex[i], 0, 0));
            if (e == null) continue;
            e.snapTo(center.getX() + ex[i], center.getY(), center.getZ() + 0.5, -90, 0);
            e.setYBodyRot(-90);
            e.setYHeadRot(-90);
            e.setNoAi(true);
        }
        // a Clydesdale knight and a Mustang horse archer
        Object[][] riders = {{com.dominionmod.dominion.entity.wild.BreedHorse.Breed.CLYDESDALE, new ItemStack(ModContent.LONGSWORD.get()), -64.5},
                {com.dominionmod.dominion.entity.wild.BreedHorse.Breed.MUSTANG, new ItemStack(Items.BOW), -59.5}};
        for (Object[] r : riders) {
            Citizen cit = ModContent.CITIZEN.get().create(level, EntitySpawnReason.COMMAND);
            if (cit == null) continue;
            cit.snapTo(center.getX() + (double) r[2], center.getY(), center.getZ() + 0.5, -90, 0);
            cit.joinKingdom(k, level);
            cit.setItemSlot(EquipmentSlot.MAINHAND, (ItemStack) r[1]);
            cit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            if (r[0] == com.dominionmod.dominion.entity.wild.BreedHorse.Breed.CLYDESDALE) cit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            level.addFreshEntity(cit);
            var h = com.dominionmod.dominion.entity.Cavalry.mountNew(level, cit, (com.dominionmod.dominion.entity.wild.BreedHorse.Breed) r[0]);
            if (h != null) {
                h.snapTo(cit.getX(), cit.getY(), cit.getZ(), -90, 0);
                h.setYBodyRot(-90);
                h.setYHeadRot(-90);
                h.setNoAi(true);
                h.setPermanentlyInvulnerable(true);
            }
            cit.setNoAi(true);
        }
        // a Shetland pony and its supply cart
        var pony = ModContent.SHETLAND_PONY.get().create(level, EntitySpawnReason.COMMAND);
        var cart = ModContent.SUPPLY_CART.get().create(level, EntitySpawnReason.COMMAND);
        if (pony != null && cart != null) {
            pony.snapTo(center.getX() - 52.5, center.getY(), center.getZ() + 0.5, -90, 0);
            pony.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            pony.setLook(pony.breed.coats[1], net.minecraft.world.entity.animal.equine.Markings.NONE);
            pony.setTamed(true);
            pony.setYBodyRot(-90);
            pony.setYHeadRot(-90);
            pony.setNoAi(true);
            pony.setPersistenceRequired();
            level.addFreshEntity(pony);
            cart.snapTo(pony.getX() - 2, pony.getY(), pony.getZ(), -90, 0);
            level.addFreshEntity(cart);
            cart.hitch(pony, k.id);
            cart.cargo().addItem(new ItemStack(Items.ARROW, 64));
            cart.cargo().addItem(new ItemStack(Items.BREAD, 16));
        }
        // the Orc Warlord, standing still for his portrait
        var lord = ModContent.ORC_WARLORD.get().create(level, EntitySpawnReason.COMMAND);
        if (lord != null) {
            lord.snapTo(center.getX() - 44.5, center.getY(), center.getZ() + 0.5, 0, 0);
            lord.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            lord.setYBodyRot(0);
            lord.setYHeadRot(0);
            lord.setNoAi(true);
            lord.setPermanentlyInvulnerable(true);
            lord.setPersistenceRequired();
            level.addFreshEntity(lord);
        }
    }

    /** The market block the screenshot run opens. */
    public static BlockPos MARKET = BlockPos.ZERO;
    public static BlockPos ILLAGERS = BlockPos.ZERO;

    /** North of the town: the new workshops, two watchtowers; far west, an Illager citadel. */
    private static void townQuarter(ServerLevel level, Kingdom k, BlockPos center) {
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-40, -1, -118), center.offset(40, 22, -63))) {
            int dy = q.getY() - center.getY();
            level.setBlock(q, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-40, -4, -118), center.offset(40, -2, -63)))
            if (level.getBlockState(q).canBeReplaced()) level.setBlock(q, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        KingdomData d = Realm.data(level);
        ChunkPos hc = ChunkPos.containing(center);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -8; dz <= -3; dz++) {
            ChunkPos cp = new ChunkPos(hc.x() + dx, hc.z() + dz);
            if (d.ownerId(level, cp) == null) d.claim(level, cp, k);
        }
        d.progress(k).researched.addAll(List.of("milling", "mining", "faith", "husbandry", "fortification"));
        Object[][] row1 = {{"mill", -30}, {"bakery", -17}, {"butcher", -3}, {"mine", 11}, {"lumber_mill", 26}};
        Object[][] row2 = {{"tavern", -28}, {"chapel", -12}, {"harbor", 3}, {"archery_range", 16}};
        for (Object[] b : row1) shop(level, k, (String) b[0], center.offset((int) b[1], -1, -74));
        for (Object[] b : row2) shop(level, k, (String) b[0], center.offset((int) b[1], -1, -98));
        instant(level, k, "lookout", center.offset(28, -1, -96), 0, 1);
        instant(level, k, "fortified_tower", center.offset(30, -1, -110), 0, 3);
        for (int i = 0; i < 4; i++) {
            var cow = net.minecraft.world.entity.EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            if (cow == null) continue;
            cow.snapTo(center.getX() - 3.5 + i * 1.5, center.getY(), center.getZ() - 66.5, 0, 0);
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            level.addFreshEntity(cow);
        }
        // a market by the town square for the Market screen
        MARKET = center.offset(5, 0, 1);
        level.setBlock(MARKET, ModContent.MARKET.get().defaultBlockState(), Block.UPDATE_ALL);
        // an Illager citadel far to the west
        ILLAGERS = new BlockPos(center.getX() - 170, 0, center.getZ() - 20);
        int ix = ILLAGERS.getX(), iz = ILLAGERS.getZ();
        BlockPos ground = new BlockPos(ix, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz) - 1, iz);
        ILLAGERS = ground;
        com.dominionmod.dominion.kingdom.Settlements.generateNow(level, com.dominionmod.dominion.kingdom.Settlements.Type.ILLAGER_CAMP, ground,
                net.minecraft.util.RandomSource.create(11));
        for (var v : d.illagerCamps) if (v.totem.distSqr(ground) < 30 * 30) com.dominionmod.dominion.kingdom.IllagerEmpire.fastForward(level, v);
        for (var m : com.dominionmod.dominion.kingdom.IllagerEmpire.members(level, ground)) m.setNoAi(true);
        // the Overlord in the war camp, for a portrait
        var lord = ModContent.ILLAGER_OVERLORD.get().create(level, EntitySpawnReason.COMMAND);
        if (lord != null) {
            lord.snapTo(center.getX() - 39.5, center.getY(), center.getZ() + 0.5, 0, 0);
            lord.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.COMMAND, null);
            lord.setNoAi(true);
            lord.setPermanentlyInvulnerable(true);
            level.addFreshEntity(lord);
        }
    }

    /** A workshop with a citizen at work inside (it hires them). */
    private static void shop(ServerLevel level, Kingdom k, String id, BlockPos at) {
        instant(level, k, id, at, 0, id.equals("archery_range") ? 2 : 1);
        if (id.equals("archery_range")) return;
        Citizen cit = ModContent.CITIZEN.get().create(level, EntitySpawnReason.COMMAND);
        if (cit == null) return;
        cit.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 180, 0);
        cit.joinKingdom(k, level);
        cit.setNoAi(true);
        level.addFreshEntity(cit);
    }

    /** North-west of the war camp: an otter pond and three pet beds with their sleepers. */
    private static void pets(ServerLevel level, ServerPlayer owner, BlockPos center) {
        BlockPos a = center.offset(-84, 0, -62), b = center.offset(-46, 0, -30);
        for (BlockPos q : BlockPos.betweenClosed(a.offset(0, -1, 0), b.offset(0, 12, 0))) {
            int dy = q.getY() - center.getY();
            level.setBlock(q, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos q : BlockPos.betweenClosed(a.offset(0, -4, 0), b.offset(0, -2, 0)))
            if (level.getBlockState(q).canBeReplaced()) level.setBlock(q, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        // the pond
        BlockPos pond = center.offset(-70, -1, -46);
        for (int dx = -5; dx <= 5; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx / 30.0 + dz * dz / 12.0 > 1) continue;
            level.setBlock(pond.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pond.offset(dx, -1, dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pond.offset(dx, -2, dz), Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int i = 0; i < 4; i++) level.setBlock(pond.offset(-6 + i * 4, 1, -4), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        Object[][] otters = {{-2.5, 0.45, 0.5, "float", 0}, {2.5, -0.35, -0.5, "swim", 1}, {-4.5, 1.0, 4.5, "sit", 0}, {-2.0, 1.0, 4.8, "pup", 0},
                {3.5, 1.0, 4.2, "stand", 1}};
        for (Object[] o : otters) {
            var ot = ModContent.OTTER.get().create(level, EntitySpawnReason.COMMAND);
            if (ot == null) continue;
            ot.snapTo(pond.getX() + 0.5 + (double) o[0], pond.getY() + (double) o[1], pond.getZ() + 0.5 + (double) o[2], 160, 0);
            ot.finalizeSpawn(level, level.getCurrentDifficultyAt(pond), EntitySpawnReason.COMMAND, null);
            ot.setVariant((int) o[4]);
            ot.setYBodyRot(160);
            ot.setYHeadRot(160);
            switch ((String) o[3]) {
                case "sit" -> {
                    ot.tame(owner);
                    ot.setOrderedToSit(true);
                    ot.setInSittingPose(true);
                }
                case "pup" -> ot.setAge(-24000);
                default -> {}
            }
            ot.setNoAi(true);
            ot.setPersistenceRequired();
            ot.setPermanentlyInvulnerable(true);
            level.addFreshEntity(ot);
        }
        // three pet beds with a cat, a dog and an otter asleep in them
        net.minecraft.world.item.DyeColor[] colors = {net.minecraft.world.item.DyeColor.RED, net.minecraft.world.item.DyeColor.BLUE,
                net.minecraft.world.item.DyeColor.YELLOW, net.minecraft.world.item.DyeColor.GREEN};
        for (int i = 0; i < 4; i++) {
            BlockPos bed = center.offset(-62 + i * 2, 0, -38);
            level.setBlock(bed, ModContent.PET_BED.get().defaultBlockState().setValue(com.dominionmod.dominion.block.PetBedBlock.COLOR, colors[i])
                    .setValue(com.dominionmod.dominion.block.PetBedBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
            if (i == 3) continue;
            net.minecraft.world.entity.TamableAnimal pet = switch (i) {
                case 0 -> net.minecraft.world.entity.EntityTypes.CAT.create(level, EntitySpawnReason.COMMAND);
                case 1 -> net.minecraft.world.entity.EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
                default -> ModContent.OTTER.get().create(level, EntitySpawnReason.COMMAND);
            };
            if (pet == null) continue;
            pet.snapTo(bed.getX() + 0.5, bed.getY() + 0.25, bed.getZ() + 0.5, 180, 0);
            pet.finalizeSpawn(level, level.getCurrentDifficultyAt(bed), EntitySpawnReason.COMMAND, null);
            pet.tame(owner);
            pet.setInSittingPose(true);
            if (pet instanceof net.minecraft.world.entity.animal.feline.Cat cat) cat.setLying(true);
            pet.setYBodyRot(180);
            pet.setYHeadRot(180);
            pet.setNoAi(true);
            pet.setPermanentlyInvulnerable(true);
            level.addFreshEntity(pet);
        }
    }
}
