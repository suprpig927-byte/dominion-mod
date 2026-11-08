package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.build.Buildings;
import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Site;
import com.dominionmod.dominion.build.Structures;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.wild.Orc;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Settlements in the wild: orc villages, small wild towns, and large wild kingdoms with a castle.
 *
 * <p>The overworld is cut into 512-block regions. Each region rolls (from the world seed) whether it
 * holds a settlement, what kind, and where. When a player comes within sight of an empty site whose
 * land is loaded, it is built: terrain levelled, buildings raised, people spawned, land claimed.
 * Building is spread over a few ticks so the server doesn't stall.
 */
public final class Settlements {
    private Settlements() {}

    public enum Type {
        ORC_VILLAGE(26), WILD_TOWN(32), WILD_KINGDOM(48);

        public final int radius;

        Type(int radius) {
            this.radius = radius;
        }
    }

    public static final int REGION = 512;
    private static final Deque<Runnable> JOBS = new ArrayDeque<>();

    // ------------------------------------------------------------------ discovery

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        for (int i = 0; i < 2 && !JOBS.isEmpty(); i++) {
            try {
                JOBS.poll().run();
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Settlement building step failed", e);
            }
        }
        if (level.getGameTime() % 40 != 7 || !JOBS.isEmpty()) return;
        KingdomData d = Realm.data(level);
        for (ServerPlayer p : level.players()) {
            int rx0 = Math.floorDiv(p.getBlockX(), REGION), rz0 = Math.floorDiv(p.getBlockZ(), REGION);
            for (int rx = rx0 - 1; rx <= rx0 + 1; rx++) {
                for (int rz = rz0 - 1; rz <= rz0 + 1; rz++) {
                    long key = ChunkPos.pack(rx, rz);
                    if (d.settled.contains(key)) continue;
                    RandomSource r = regionRandom(level, rx, rz);
                    float roll = r.nextFloat();
                    Type type = roll < 0.30f ? Type.ORC_VILLAGE : roll < 0.48f ? Type.WILD_TOWN : roll < 0.60f ? Type.WILD_KINGDOM : null;
                    int cx = rx * REGION + 96 + r.nextInt(REGION - 192), cz = rz * REGION + 96 + r.nextInt(REGION - 192);
                    if (type == null) {
                        d.settled.add(key);
                        d.markChanged();
                        continue;
                    }
                    double dist = Math.sqrt(p.distanceToSqr(cx + 0.5, p.getY(), cz + 0.5));
                    if (dist < 56 || dist > 200 || !loaded(level, cx, cz, type.radius + 8)) continue;
                    d.settled.add(key);
                    d.markChanged();
                    BlockPos center = surface(level, cx, cz);
                    String why = unsuitable(level, type, center);
                    if (why != null) {
                        DominionMod.LOGGER.info("Skipped a {} at {}: {}", type, center, why);
                        continue;
                    }
                    DominionMod.LOGGER.info("Raising a {} at {}", type, center);
                    JOBS.addAll(plan(level, type, center, r));
                }
            }
        }
    }

    private static RandomSource regionRandom(ServerLevel level, int rx, int rz) {
        return RandomSource.create(level.getSeed() ^ (rx * 341873128712L) ^ (rz * 132897987541L) ^ 0x5E771EL);
    }

    private static boolean loaded(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16) {
            for (int dz = -radius; dz <= radius; dz += 16) {
                if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) return false;
            }
        }
        return true;
    }

    /** The top solid block (not leaves) of a column. */
    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static @Nullable String unsuitable(ServerLevel level, Type type, BlockPos c) {
        var spawn = level.getRespawnData().pos();
        if (spawn.distSqr(c) < (type == Type.ORC_VILLAGE ? 260 * 260 : 360 * 360)) return "too near the world spawn";
        var biome = level.getBiome(c);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) return "water biome";
        if (!level.getFluidState(c).isEmpty() || !level.getFluidState(c.above()).isEmpty()) return "in water";
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0;
        int r = type.radius - 6;
        for (int dx = -r; dx <= r; dx += 6) {
            for (int dz = -r; dz <= r; dz += 6) {
                BlockPos s = surface(level, c.getX() + dx, c.getZ() + dz);
                lo = Math.min(lo, s.getY());
                hi = Math.max(hi, s.getY());
                if (!level.getFluidState(s).isEmpty()) wet++;
            }
        }
        if (hi - lo > (type == Type.WILD_KINGDOM ? 14 : 12)) return "too steep";
        if (wet > 4) return "too much water";
        KingdomData d = Realm.data(level);
        for (int dx = -type.radius; dx <= type.radius; dx += 16) {
            for (int dz = -type.radius; dz <= type.radius; dz += 16) {
                if (d.ownerAt(level, c.offset(dx, 0, dz)) != null) return "claimed land";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ planning

    private record Spot(String structure, BlockPos ground, int rot, int tier) {}

    /** Builds a settlement right away (admin command and tests). */
    public static void generateNow(ServerLevel level, Type type, BlockPos center, RandomSource r) {
        for (Runnable job : plan(level, type, center, r)) job.run();
    }

    private static List<Runnable> plan(ServerLevel level, Type type, BlockPos center, RandomSource r) {
        return switch (type) {
            case ORC_VILLAGE -> orcVillage(level, center, r);
            case WILD_TOWN -> wildKingdom(level, center, r, false);
            case WILD_KINGDOM -> wildKingdom(level, center, r, true);
        };
    }

    /** Lays buildings out on a ring round the centre, none overlapping, each facing the middle. */
    private static List<Spot> ring(ServerLevel level, BlockPos center, RandomSource r, List<String> ids, List<Integer> tiers,
                                   double inner, double outer) {
        List<Spot> out = new ArrayList<>();
        List<double[]> taken = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Structures.Structure s = Structures.get(ids.get(i));
            if (s == null) continue;
            double rad = Math.max(s.width(), s.depth()) / 2.0 + 1.5;
            for (int attempt = 0; attempt < 40; attempt++) {
                double dist = inner + rad + r.nextDouble() * Math.max(1, outer - inner - rad);
                double ang = r.nextDouble() * Math.PI * 2;
                double x = center.getX() + Math.cos(ang) * dist, z = center.getZ() + Math.sin(ang) * dist;
                boolean ok = true;
                for (double[] t : taken) {
                    if (Math.hypot(t[0] - x, t[1] - z) < t[2] + rad + 1.5) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                taken.add(new double[]{x, z, rad});
                BlockPos col = BlockPos.containing(x, 0, z);
                int y = groundFor(level, s, col, center.getY());
                Direction look = Math.abs(x - center.getX()) > Math.abs(z - center.getZ())
                        ? (x > center.getX() ? Direction.EAST : Direction.WEST)
                        : (z > center.getZ() ? Direction.SOUTH : Direction.NORTH);
                out.add(new Spot(ids.get(i), col.atY(y), Plans.rotationFacing(look), tiers.get(i)));
                break;
            }
        }
        return out;
    }

    /** The height to build at: the middle of the ground under the footprint, kept near the settlement's centre. */
    private static int groundFor(ServerLevel level, Structures.Structure s, BlockPos col, int centerY) {
        List<Integer> ys = new ArrayList<>();
        int hw = s.width() / 2, hd = s.depth() / 2;
        for (int dx = -hw; dx <= hw; dx += Math.max(1, hw)) {
            for (int dz = -hd; dz <= hd; dz += Math.max(1, hd)) ys.add(surface(level, col.getX() + dx, col.getZ() + dz).getY());
        }
        ys.sort(Integer::compare);
        int y = ys.get(ys.size() / 2);
        return Math.max(centerY - 5, Math.min(centerY + 5, y));
    }

    // ------------------------------------------------------------------ orc villages

    private static List<Runnable> orcVillage(ServerLevel level, BlockPos center, RandomSource r) {
        List<Runnable> jobs = new ArrayList<>();
        Kingdom paint = new Kingdom("Orcs", new UUID(0, 0), 12, 15, ItemStack.EMPTY);
        int huts = 4 + r.nextInt(3);
        List<String> ids = new ArrayList<>();
        List<Integer> tiers = new ArrayList<>();
        for (int i = 0; i < huts; i++) {
            ids.add("orc_hut");
            tiers.add(0);
        }
        List<Spot> spots = ring(level, center, r, ids, tiers, 5, 15);
        jobs.add(() -> {
            place(level, paint, "orc_totem", center, 0, 0, false);
            Structures.Structure totem = Structures.get("orc_totem");
            BlockPos at = Plans.cell(totem, center, 0, 2, 4, 2);
            Realm.data(level).orcVillages.add(new KingdomData.OrcVillage(KingdomData.dim(level), at, false));
            Realm.data(level).markChanged();
        });
        for (Spot s : spots) {
            jobs.add(() -> {
                for (BlockPos chest : place(level, paint, s.structure(), s.ground(), s.rot(), 0, false)) fillOrcChest(level, chest, r);
            });
        }
        jobs.add(() -> {
            int n = 6 + r.nextInt(4);
            for (int i = 0; i <= n; i++) {
                Orc orc = ModContent.ORC.get().create(level, EntitySpawnReason.STRUCTURE);
                if (orc == null) continue;
                double a = r.nextDouble() * Math.PI * 2, dist = i == 0 ? 3 : 4 + r.nextDouble() * 10;
                BlockPos at = surface(level, center.getX() + (int) (Math.cos(a) * dist), center.getZ() + (int) (Math.sin(a) * dist)).above();
                orc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
                orc.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
                if (i == 0) orc.makeChief();
                orc.setPersistenceRequired();
                orc.setHomeTo(center, 22);
                level.addFreshEntity(orc);
            }
        });
        return jobs;
    }

    private static void fillOrcChest(ServerLevel level, BlockPos pos, RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        ItemStack[] loot = {
                new ItemStack(ModContent.GOLD_COIN.get(), 3 + r.nextInt(8)), new ItemStack(Items.BONE, 2 + r.nextInt(5)),
                new ItemStack(Items.LEATHER, 1 + r.nextInt(4)), new ItemStack(Items.COOKED_PORKCHOP, 1 + r.nextInt(3)),
                new ItemStack(Items.IRON_INGOT, r.nextInt(4)), new ItemStack(ModContent.BULLET.get(), r.nextInt(3) == 0 ? 8 : 0),
                new ItemStack(Items.ARROW, r.nextInt(12))};
        int slot = 0;
        for (ItemStack s : loot) if (!s.isEmpty()) chest.setItem(slot++ * 3 % chest.getContainerSize(), s);
    }

    // ------------------------------------------------------------------ wild kingdoms

    private static final String[] PRE = {"Al", "Bel", "Cor", "Dun", "Eld", "Fal", "Gar", "Hal", "Ith", "Kar", "Lor", "Mar", "Nor", "Os",
            "Riv", "Stor", "Thal", "Val", "Wy", "Ash", "Bram", "Cael", "Dra", "Fen"};
    private static final String[] SUF = {"mere", "holt", "wick", "ford", "gard", "heim", "moor", "dale", "crest", "vale", "stead", "march",
            "haven", "reach"};
    private static final String[] PATTERNS = {"minecraft:stripe_center", "minecraft:cross", "minecraft:half_horizontal", "minecraft:border",
            "minecraft:circle", "minecraft:triangle_top", "minecraft:rhombus", "minecraft:stripe_top", "minecraft:straight_cross",
            "minecraft:diagonal_left"};

    private static List<Runnable> wildKingdom(ServerLevel level, BlockPos center, RandomSource r, boolean large) {
        List<Runnable> jobs = new ArrayList<>();
        KingdomData d = Realm.data(level);
        String name = PRE[r.nextInt(PRE.length)] + SUF[r.nextInt(SUF.length)];
        int primary = r.nextInt(16), secondary;
        do secondary = r.nextInt(16); while (secondary == primary);
        StringBuilder flag = new StringBuilder(primary + ";");
        int layers = 1 + r.nextInt(2);
        for (int i = 0; i < layers; i++) {
            if (i > 0) flag.append(',');
            flag.append(PATTERNS[r.nextInt(PATTERNS.length)]).append('#').append(i == 0 ? secondary : r.nextInt(16));
        }
        Kingdom k = new Kingdom(large ? "Kingdom of " + name : name, new UUID(r.nextLong(), r.nextLong()), primary, secondary,
                Flags.toBanner(flag.toString(), level.registryAccess()));
        k.wild = true;
        k.peaceful = false;
        k.hallDim = KingdomData.dim(level);
        int tier = large ? 2 + r.nextInt(3) : r.nextInt(3);
        Progress pr = d.progress(k);
        for (String t : new String[]{"timber", "stone", "brick", "royal"}) {
            if (Palette_tierIndex(t) <= tier) pr.researched.add(t);
        }

        // what to build
        List<String> ids = new ArrayList<>();
        List<Integer> tiers = new ArrayList<>();
        int homes = large ? 6 + r.nextInt(3) : 3 + r.nextInt(2);
        for (int i = 0; i < homes; i++) {
            String home = Structures.HOMES.get(r.nextInt(Structures.HOMES.size()));
            if (r.nextInt(large ? 2 : 4) == 0 && Structures.storeyOf(home) != null) home = home + "_2";
            ids.add(home);
            tiers.add(Math.max(0, tier - r.nextInt(2)));
        }
        List<String> extra = new ArrayList<>(List.of("farm", "market_stall"));
        if (large) extra.addAll(List.of("barracks", "storehouse", "smithy", "library", "farm", "market_stall"));
        else if (tier >= 2) extra.add("watchtower");
        else extra.add("storehouse");
        for (String e : extra) {
            ids.add(e);
            tiers.add(tier);
        }
        java.util.Collections.shuffle(ids, new java.util.Random(r.nextLong()));
        // keep tiers lined up with the shuffled ids: recompute by id
        tiers.clear();
        for (String id : ids) tiers.add(id.startsWith("farm") || id.equals("market_stall") ? tier : Math.max(0, tier - r.nextInt(2)));

        double inner = large ? 15 : 4, outer = large ? 40 : 24;
        List<Spot> spots = ring(level, center, r, ids, tiers, inner, outer);
        BlockPos hall = large ? null : center.above();

        jobs.add(() -> {
            d.add(k);
            int claimR = large ? 2 : 1;
            ChunkPos cc = ChunkPos.containing(center);
            for (int dx = -claimR; dx <= claimR; dx++) {
                for (int dz = -claimR; dz <= claimR; dz++) {
                    ChunkPos cp = new ChunkPos(cc.x() + dx, cc.z() + dz);
                    if (d.ownerId(level, cp) == null) d.claim(level, cp, k);
                }
            }
            BlockPos h;
            if (large) {
                place(level, k, "castle", center, 0, Math.max(2, tier), true);
                h = Plans.cell(Structures.get("castle"), center, 0, 15, 1, 15);
            } else {
                levelPlot(level, center, 3, 6);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setBlock(center.offset(dx, 0, dz),
                        Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                h = hall;
            }
            level.setBlock(h, ModContent.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
            k.hall = Optional.of(h.immutable());
            Realm.raiseFlag(level, h, k, null);
            d.markChanged();
        });
        for (Spot s : spots) {
            jobs.add(() -> {
                place(level, k, s.structure(), s.ground(), s.rot(), s.tier(), true);
                if (s.structure().equals("storehouse")) {
                    k.storehouses.add(s.ground().above().asLong());
                    for (BlockPos chest : chestsIn(level, s)) stockStorehouse(level, chest, r);
                }
            });
        }
        jobs.add(() -> populate(level, k, center, spots, r, large));
        return jobs;
    }

    private static int Palette_tierIndex(String research) {
        String[] t = com.dominionmod.dominion.build.Palette.TIER_RESEARCH;
        for (int i = 0; i < t.length; i++) if (research.equals(t[i])) return i;
        return 99;
    }

    private static List<BlockPos> chestsIn(ServerLevel level, Spot s) {
        List<BlockPos> out = new ArrayList<>();
        Structures.Structure st = Structures.get(s.structure());
        if (st == null) return out;
        BlockPos[] c = Plans.corners(st, s.ground(), s.rot());
        for (BlockPos p : BlockPos.betweenClosed(c[0], c[1].above(4))) {
            if (level.getBlockEntity(p) instanceof ChestBlockEntity) out.add(p.immutable());
        }
        return out;
    }

    private static void stockStorehouse(ServerLevel level, BlockPos pos, RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        chest.setItem(0, new ItemStack(Items.BREAD, 8 + r.nextInt(16)));
        chest.setItem(1, new ItemStack(ModContent.GOLD_COIN.get(), 10 + r.nextInt(30)));
        chest.setItem(2, new ItemStack(Items.WHEAT, r.nextInt(20)));
        chest.setItem(3, new ItemStack(Items.IRON_INGOT, r.nextInt(8)));
    }

    /** Townsfolk and soldiers. Some guards walk a patrol round the town. */
    private static void populate(ServerLevel level, Kingdom k, BlockPos center, List<Spot> spots, RandomSource r, boolean large) {
        String look = level.getBiome(center).is(BiomeTags.IS_TAIGA) ? "taiga" : level.getBiome(center).is(BiomeTags.IS_SAVANNA) ? "savanna" : "plains";
        List<ItemStack[]> people = new ArrayList<>();
        // {main hand, off hand, chest, head}
        for (int i = 0; i < (large ? 4 : 3); i++) people.add(new ItemStack[]{new ItemStack(Items.IRON_HOE)});
        people.add(new ItemStack[]{new ItemStack(Items.IRON_AXE)});
        int guards = large ? 6 : 2;
        for (int i = 0; i < guards; i++) {
            ItemStack weapon = new ItemStack(large ? (i % 3 == 0 ? ModContent.HALBERD.get() : ModContent.LONGSWORD.get()) : Items.IRON_SWORD);
            people.add(new ItemStack[]{weapon, new ItemStack(i % 3 == 0 ? Items.AIR : ModContent.IRON_SHIELD.get()),
                    new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET)});
        }
        for (int i = 0; i < (large ? 3 : 2); i++) people.add(new ItemStack[]{new ItemStack(Items.BOW), ItemStack.EMPTY,
                new ItemStack(Items.LEATHER_CHESTPLATE)});
        if (large) {
            for (int i = 0; i < 2; i++) people.add(new ItemStack[]{new ItemStack(ModContent.MUSKET.get()), ItemStack.EMPTY,
                    new ItemStack(Items.IRON_CHESTPLATE)});
        }
        List<BlockPos> route = new ArrayList<>();
        double rr = large ? 30 : 16;
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            route.add(surface(level, center.getX() + (int) (Math.cos(a) * rr), center.getZ() + (int) (Math.sin(a) * rr)).above());
        }
        int patrolling = 0;
        for (int i = 0; i < people.size(); i++) {
            ItemStack[] kit = people.get(i);
            BlockPos near = spots.isEmpty() ? center : spots.get(i % spots.size()).ground();
            BlockPos at = surface(level, near.getX() + r.nextInt(7) - 3, near.getZ() + r.nextInt(7) - 3).above();
            if (kit[0].is(net.minecraft.tags.ItemTags.SWORDS)) {
                at = surface(level, center.getX() + r.nextInt(9) - 4, center.getZ() + 8 + r.nextInt(5)).above();
            }
            Citizen c = spawnCitizen(level, k, at, kit, look, r);
            if (c != null && c.job().soldier && c.job().melee() && patrolling < (large ? 3 : 1)) {
                c.patrol(route);
                patrolling++;
            }
        }
        if (large) {
            Citizen wiz = spawnCitizen(level, k, center.above(), new ItemStack[]{ItemStack.EMPTY}, look, r);
            if (wiz != null) wiz.train(Job.WIZARD);
        }
        Realm.data(level).markChanged();
    }

    private static @Nullable Citizen spawnCitizen(ServerLevel level, Kingdom k, BlockPos at, ItemStack[] kit, String look, RandomSource r) {
        Citizen c = ModContent.CITIZEN.get().create(level, EntitySpawnReason.STRUCTURE);
        if (c == null) return null;
        c.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
        c.setVillagerTypeForDebug(look);
        if (kit.length > 0 && !kit[0].isEmpty()) c.setItemSlot(EquipmentSlot.MAINHAND, kit[0]);
        if (kit.length > 1 && !kit[1].isEmpty()) c.setItemSlot(EquipmentSlot.OFFHAND, kit[1]);
        if (kit.length > 2 && !kit[2].isEmpty()) c.setItemSlot(EquipmentSlot.CHEST, kit[2]);
        if (kit.length > 3 && !kit[3].isEmpty()) c.setItemSlot(EquipmentSlot.HEAD, kit[3]);
        level.addFreshEntity(c);
        c.joinKingdom(k, level);
        c.setPost(at);
        return c;
    }

    // ------------------------------------------------------------------ building

    /** Flattens the ground for a plot: fills hollows with dirt, clears hills, trees and plants above. */
    private static void levelPlot(ServerLevel level, BlockPos ground, int halfW, int clearHeight) {
        levelBox(level, ground, ground.getX() - halfW, ground.getZ() - halfW, ground.getX() + halfW, ground.getZ() + halfW, clearHeight);
    }

    private static void levelBox(ServerLevel level, BlockPos ground, int x0, int z0, int x1, int z1, int clearHeight) {
        int gy = ground.getY();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                for (int y = gy + 1; y <= Math.max(top, gy + clearHeight); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                int solid = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                for (int y = Math.min(solid, gy) + 1; y < gy; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockPos g = new BlockPos(x, gy, z);
                var st = level.getBlockState(g);
                if (st.canBeReplaced() || !st.getFluidState().isEmpty() || st.is(Blocks.DIRT) || st.isAir()) {
                    level.setBlock(g, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Builds a structure on the spot (no builders, no materials), after levelling its plot.
     * Returns the chests it placed. With {@code record}, the kingdom gets the building (and its sign).
     */
    public static List<BlockPos> place(ServerLevel level, Kingdom k, String id, BlockPos ground, int rot, int tier, boolean record) {
        List<BlockPos> chests = new ArrayList<>();
        Structures.Structure s = Structures.get(id);
        if (s == null) return chests;
        BlockPos[] c = Plans.corners(s, ground, rot);
        levelBox(level, ground, Math.min(c[0].getX(), c[1].getX()) - 1, Math.min(c[0].getZ(), c[1].getZ()) - 1,
                Math.max(c[0].getX(), c[1].getX()) + 1, Math.max(c[0].getZ(), c[1].getZ()) + 1, s.height() + 3);
        Site site = new Site(UUID.randomUUID(), k.id, id, KingdomData.dim(level), ground, ground, rot, List.of(), 0);
        site.tier = tier;
        List<Plans.Step> steps = Plans.steps(level, site, k);
        for (Plans.Step st : steps) {
            if (st.isClear()) {
                level.setBlock(st.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                continue;
            }
            level.setBlock(st.pos(), st.state(), Block.UPDATE_ALL);
            if (st.companion() != null) {
                level.setBlock(st.companionPos(), st.companion(), Block.UPDATE_ALL);
                level.setBlock(st.pos(), st.state(), Block.UPDATE_ALL);
            }
            if (st.flag() && level.getBlockEntity(st.pos()) != null) level.getBlockEntity(st.pos()).applyComponentsFromItemStack(k.flag);
            if (st.state().is(Blocks.CHEST)) chests.add(st.pos());
        }
        for (Plans.Step st : steps) {
            if (st.isClear() || st.companion() != null) continue;
            var now = level.getBlockState(st.pos());
            var upd = Block.updateFromNeighbourShapes(now, level, st.pos());
            if (!upd.isAir() && upd != now) level.setBlock(st.pos(), upd, Block.UPDATE_CLIENTS);
        }
        Plans.forget(site.id);
        if (record) Buildings.finished(level, k, site);
        return chests;
    }

    // ------------------------------------------------------------------ conquest

    /** Soldiers of a wild kingdom still standing near its Town Hall. */
    public static int defenders(ServerLevel level, Kingdom wild, BlockPos hall) {
        return level.getEntitiesOfClass(Citizen.class, new net.minecraft.world.phys.AABB(hall).inflate(64),
                c -> c.isAlive() && c.sameKingdom(wild.id) && c.job().soldier).size();
    }

    /** A player clicked a wild kingdom's Town Hall: if its defenders are beaten, the kingdom becomes theirs. */
    public static void tryConquer(ServerPlayer p, Kingdom wild, BlockPos hall) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Kingdom mine = d.byPlayer(p.getUUID());
        if (mine == null) {
            p.sendSystemMessage(Realm.err("Found a kingdom of your own before you try to take " + wild.name + "."));
            return;
        }
        int left = defenders(level, wild, hall);
        if (left > 0) {
            p.sendSystemMessage(Component.literal(left + " soldiers of " + wild.name + " still defend it. Defeat them, then claim the Town Hall.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        conquer(level, wild, mine);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(mine.name + " has conquered " + wild.name + "!")
                .withStyle(ChatFormatting.GOLD), false);
        level.playSound(null, hall, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3.0f, 1.0f);
    }

    /** Hands a wild kingdom's land, buildings, storehouses and surviving people to the conqueror. */
    public static void conquer(ServerLevel level, Kingdom wild, Kingdom mine) {
        KingdomData d = Realm.data(level);
        List<ChunkPos> land = new ArrayList<>();
        int claimR = 4;
        if (wild.hall.isPresent()) {
            ChunkPos hc = ChunkPos.containing(wild.hall.get());
            for (int dx = -claimR; dx <= claimR; dx++) {
                for (int dz = -claimR; dz <= claimR; dz++) {
                    ChunkPos cp = new ChunkPos(hc.x() + dx, hc.z() + dz);
                    if (wild.id.equals(d.ownerId(level, cp))) land.add(cp);
                }
            }
        }
        Progress from = d.progress(wild), to = d.progress(mine);
        to.buildings.addAll(from.buildings);
        mine.storehouses.addAll(wild.storehouses);
        List<Citizen> people = level.getEntitiesOfClass(Citizen.class,
                new net.minecraft.world.phys.AABB(wild.hall.orElse(BlockPos.ZERO)).inflate(96), c -> c.sameKingdom(wild.id));
        d.remove(wild);
        for (ChunkPos cp : land) d.claim(level, cp, mine);
        for (Citizen c : people) {
            c.returnToDuty();
            c.joinKingdom(mine, level);
        }
        for (var b : to.buildings) Buildings.placeSign(level, b);
        d.markChanged();
    }

    /** A player clicked an orc War Totem. */
    public static void tryClaimOrcVillage(ServerPlayer p, BlockPos totem) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        KingdomData.OrcVillage v = null;
        for (KingdomData.OrcVillage o : d.orcVillages) {
            if (o.dim.equals(KingdomData.dim(level)) && o.totem.distSqr(totem) < 9) v = o;
        }
        if (v == null) {
            v = new KingdomData.OrcVillage(KingdomData.dim(level), totem.immutable(), false);
            d.orcVillages.add(v);
        }
        if (v.conquered) {
            p.sendOverlayMessage(Component.literal("This village has already been taken.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int orcs = level.getEntitiesOfClass(Orc.class, new net.minecraft.world.phys.AABB(totem).inflate(40), Orc::isAlive).size();
        if (orcs > 0) {
            p.sendSystemMessage(Component.literal(orcs + " orcs still hold this village. Drive them out, then claim the totem.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Kingdom mine = d.byPlayer(p.getUUID());
        if (mine == null) {
            p.sendSystemMessage(Realm.err("Found a kingdom first: then this land can be yours."));
            return;
        }
        ChunkPos hc = ChunkPos.containing(totem);
        int got = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos cp = new ChunkPos(hc.x() + dx, hc.z() + dz);
                if (d.ownerId(level, cp) == null) {
                    d.claim(level, cp, mine);
                    got++;
                }
            }
        }
        v.conquered = true;
        d.markChanged();
        Realm.give(p, new ItemStack(ModContent.GOLD_COIN.get(), 25));
        level.playSound(null, totem, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3.0f, 1.2f);
        p.sendSystemMessage(Component.literal("The orc village is yours: " + got + " chunks of land and 25 gold coins of plunder.")
                .withStyle(ChatFormatting.GOLD));
    }
}
