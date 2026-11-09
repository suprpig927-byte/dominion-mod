package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.wild.IllagerOverlord;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Illager empire: outposts in the wild that grow into forts and then citadels ruled by an
 * Overlord. Every few days, at first light, a camp sends a war party (pillagers and vindicators,
 * then evokers, witches and ravagers) to sack the nearest town. Clear a camp and claim its Illager
 * Standard to take the land, the plunder, and any villagers caged there.
 */
public final class IllagerEmpire {
    private IllagerEmpire() {}

    /** The "kingdom" Illager war parties march for. */
    public static final UUID ILLAGERS = new UUID(0x111A6E5L, 0x111A6E5L);
    private static final String TAG = "DominionIllager";
    private static final int[] CAP = {7, 11, 15};
    private static final float[] RAID_CHANCE = {0.25f, 0.4f, 0.55f};
    private static final int[] RAID_RANGE = {700, 850, 1000};
    private static final int RADIUS = 20;

    public static String title(KingdomData.OrcVillage v) {
        return v.stage >= 2 ? "Illager citadel" : v.stage == 1 ? "Illager fort" : "Illager outpost";
    }

    public static int wantedStage(KingdomData.OrcVillage v) {
        return v.days >= 8 ? 2 : v.days >= 4 ? 1 : 0;
    }

    // ------------------------------------------------------------------ the camps' lives

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        long t = level.getGameTime();
        KingdomData d = Realm.data(level);
        long day = Diplomacy.day(level);
        long dayTime = level.getOverworldClockTime() % 24000L;
        boolean dawn = dayTime >= 500 && dayTime < 900;
        if (t % 200 != 131 && !(dawn && t % 40 == 23)) return;
        for (KingdomData.OrcVillage v : d.illagerCamps) {
            if (v.conquered || !v.dim.equals(KingdomData.dim(level))) continue;
            try {
                if (v.lastDay < 0) v.lastDay = day;
                if (v.lastDay < day) {
                    v.lastDay = day;
                    v.days++;
                    d.markChanged();
                }
                if (awake(level, v.totem)) grow(level, d, v);
                if (dawn && v.lastRaid < day) {
                    v.lastRaid = day;
                    d.markChanged();
                    maybeRaid(level, d, v);
                }
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Illager camp at {} misbehaved", v.totem, e);
            }
        }
    }

    private static boolean awake(ServerLevel level, BlockPos at) {
        if (!level.isLoaded(at)) return false;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(at.getX(), p.getY(), at.getZ()) < 120 * 120) return true;
        return false;
    }

    static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static Kingdom paint() {
        return new Kingdom("Illagers", new UUID(0, 0), 7, 15, ItemStack.EMPTY);
    }

    private static void grow(ServerLevel level, KingdomData d, KingdomData.OrcVillage v) {
        RandomSource r = level.getRandom();
        int want = wantedStage(v);
        if (want > v.stage) {
            v.stage = want;
            d.markChanged();
            BlockPos c = v.totem;
            if (want >= 1) {
                wall(level, c, want >= 2);
                Settlements.place(level, paint(), "illager_tent", surface(level, c.getX() + 12, c.getZ() + 6), 1, 0, false);
                Settlements.place(level, paint(), "illager_tent", surface(level, c.getX() - 12, c.getZ() + 6), 3, 0, false);
                ravagerPen(level, surface(level, c.getX() + 8, c.getZ() + 13));
            }
            if (want >= 2) {
                Settlements.place(level, paint(), "illager_hall", surface(level, c.getX(), c.getZ() - 14), 0, 0, false);
                Settlements.place(level, paint(), "illager_tower", surface(level, c.getX() - 14, c.getZ() - 12), 0, 0, false);
            }
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(c.getX(), p.getY(), c.getZ()) < 160 * 160)
                p.sendSystemMessage(Component.literal(want >= 2 ? "The Illagers have raised a citadel, and an Overlord rules it from his hall."
                        : "The Illager outpost has grown into a walled fort.").withStyle(ChatFormatting.DARK_PURPLE));
        }
        List<Mob> members = members(level, v.totem);
        if (v.stage >= 2 && !v.warlordSlain && members.stream().noneMatch(m -> m instanceof IllagerOverlord)) {
            spawnMember(level, ModContent.ILLAGER_OVERLORD.get(), surface(level, v.totem.getX(), v.totem.getZ() - 18).above(), v.totem);
        }
        if (members.size() < CAP[v.stage] && level.getGameTime() % 2400 < 200) {
            long evokers = members.stream().filter(m -> m.getType() == EntityTypes.EVOKER).count();
            long ravagers = members.stream().filter(m -> m.getType() == EntityTypes.RAVAGER).count();
            for (int i = 0; i < 2 && members.size() + i < CAP[v.stage]; i++) {
                EntityType<? extends Mob> type = v.stage >= 1 && evokers == 0 ? EntityTypes.EVOKER
                        : v.stage >= 2 && ravagers == 0 ? EntityTypes.RAVAGER
                        : r.nextFloat() < 0.15f && v.stage >= 1 ? EntityTypes.WITCH
                        : r.nextBoolean() ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR;
                if (type == EntityTypes.EVOKER) evokers++;
                if (type == EntityTypes.RAVAGER) ravagers++;
                double a = r.nextDouble() * Math.PI * 2, dist = 4 + r.nextDouble() * 12;
                spawnMember(level, type, surface(level, v.totem.getX() + (int) (Math.cos(a) * dist), v.totem.getZ() + (int) (Math.sin(a) * dist)).above(), v.totem);
            }
        }
    }

    /** Illagers belonging to the camp (within its walls). */
    public static List<Mob> members(ServerLevel level, BlockPos camp) {
        return level.getEntitiesOfClass(Mob.class, new AABB(camp).inflate(40, 20, 40), m -> m.isAlive() && isIllager(m));
    }

    public static boolean isIllager(Entity e) {
        return e instanceof Raider || e instanceof IllagerOverlord || e.getType() == EntityTypes.RAVAGER;
    }

    private static @Nullable Mob spawnMember(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, BlockPos home) {
        Mob m = type.create(level, EntitySpawnReason.STRUCTURE);
        if (m == null) return null;
        m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
        if (m.getType() == EntityTypes.VINDICATOR) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        m.setPersistenceRequired();
        m.setHomeTo(home, type == EntityTypes.RAVAGER ? 10 : 22);
        enlist(m);
        level.addFreshEntity(m);
        return m;
    }

    /** Marks a mob as one of the empire's: it hunts citizens and keeps to its post (goals added on load, see {@link #onJoin}). */
    public static void enlist(Mob m) {
        m.getPersistentData().putBoolean(TAG, true);
        if (m instanceof Raider raider) raider.setCanJoinRaid(false);
        onJoin(m);
    }

    /** Marks the goals we've added, so they're added once per loaded mob. */
    private static final class Keep extends MoveTowardsRestrictionGoal {
        Keep(PathfinderMob m) {
            super(m, 1.0);
        }
    }

    /** Adds the empire's goals to one of its mobs (on spawning, and again each time it's loaded). */
    public static void onJoin(Mob m) {
        if (!m.getPersistentData().getBooleanOr(TAG, false) || !(m instanceof PathfinderMob pm)) return;
        if (pm.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof Keep)) return;
        pm.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(pm, Citizen.class, true));
        pm.goalSelector.addGoal(5, new Keep(pm));
    }

    // ------------------------------------------------------------------ fortifications

    /** A cobblestone wall round the camp with dark oak posts and four gates; a citadel's is taller with a walkway. */
    private static void wall(ServerLevel level, BlockPos center, boolean tall) {
        int r = RADIUS + 2, h = tall ? 4 : 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(dist - r) > 0.55) continue;
                if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) continue;
                BlockPos g = surface(level, center.getX() + dx, center.getZ() + dz);
                if (!level.getFluidState(g).isEmpty()) continue;
                boolean post = (dx * 7 + dz * 13) % 6 == 0;
                for (int y = 1; y <= h; y++)
                    level.setBlock(g.above(y), post ? Blocks.DARK_OAK_LOG.defaultBlockState()
                            : (y + dx + dz) % 5 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(g.above(h + 1), post ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void ravagerPen(ServerLevel level, BlockPos at) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
            BlockPos g = surface(level, at.getX() + dx, at.getZ() + dz);
            level.setBlock(g.above(), Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(g.above(2), Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(surface(level, at.getX(), at.getZ()).above(), Blocks.HAY_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    // ------------------------------------------------------------------ new camps (called by Settlements)

    /** Builds an Illager outpost: standard, tower, tents, a cage of captives, and the garrison. */
    static List<Runnable> outpost(ServerLevel level, BlockPos center, RandomSource r) {
        List<Runnable> jobs = new ArrayList<>();
        jobs.add(() -> {
            Settlements.place(level, paint(), "illager_standard", center, 0, 0, false);
            var st = com.dominionmod.dominion.build.Structures.get("illager_standard");
            BlockPos at = com.dominionmod.dominion.build.Plans.cell(st, center, 0, 2, 1, 2);
            Realm.data(level).illagerCamps.add(new KingdomData.OrcVillage(KingdomData.dim(level), at, false));
            Realm.data(level).markChanged();
        });
        jobs.add(() -> Settlements.place(level, paint(), "illager_tower", surface(level, center.getX(), center.getZ() - 10), 0, 0, false));
        jobs.add(() -> Settlements.place(level, paint(), "illager_tent", surface(level, center.getX() + 9, center.getZ() - 1), 1, 0, false));
        jobs.add(() -> Settlements.place(level, paint(), "illager_tent", surface(level, center.getX() - 9, center.getZ() - 1), 3, 0, false));
        jobs.add(() -> {
            BlockPos cage = surface(level, center.getX() + 1, center.getZ() + 9);
            Settlements.place(level, paint(), "illager_cage", cage, 0, 0, false);
            for (int i = 0; i < 2; i++) {
                Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
                if (v == null) continue;
                v.snapTo(cage.getX() + 0.5 + (i == 0 ? -1 : 1), cage.getY() + 1, cage.getZ() + 0.5, r.nextFloat() * 360, 0);
                v.finalizeSpawn(level, level.getCurrentDifficultyAt(cage), EntitySpawnReason.STRUCTURE, null);
                v.setPersistenceRequired();
                level.addFreshEntity(v);
            }
        });
        jobs.add(() -> {
            for (int i = 0; i < 6; i++) {
                double a = r.nextDouble() * Math.PI * 2, dist = 3 + r.nextDouble() * 9;
                spawnMember(level, i < 4 ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR,
                        surface(level, center.getX() + (int) (Math.cos(a) * dist), center.getZ() + (int) (Math.sin(a) * dist)).above(), center);
            }
        });
        return jobs;
    }

    // ------------------------------------------------------------------ war parties

    private static void maybeRaid(ServerLevel level, KingdomData d, KingdomData.OrcVillage v) {
        Kingdom target = null;
        double best = RAID_RANGE[v.stage];
        for (Kingdom k : d.all()) {
            if (k.wild || k.hall.isEmpty() || !k.hallDim.equals(v.dim)) continue;
            double dist = Math.sqrt(k.hall.get().distSqr(v.totem));
            if (dist < best) {
                best = dist;
                target = k;
            }
        }
        if (target == null || level.getRandom().nextFloat() > RAID_CHANCE[v.stage]) return;
        for (Armies.Army a : d.armies) if (a.illagers() && a.target.equals(target.id) && !a.returning) return;
        raid(level, v, target);
    }

    /** Sends a war party from this camp at a kingdom. */
    public static Armies.Army raid(ServerLevel level, KingdomData.OrcVillage v, Kingdom target) {
        RandomSource r = level.getRandom();
        List<String> troops = new ArrayList<>();
        int n = 4 + 2 * v.stage;
        for (int i = 0; i < n; i++) troops.add(i % 2 == 0 ? "pillager" : "vindicator");
        if (v.stage >= 1) {
            troops.add("evoker");
            troops.add("witch");
        }
        if (v.stage >= 2) troops.add("ravager");
        if (v.stage >= 2 && !v.warlordSlain && r.nextFloat() < 0.25f) troops.add("overlord");
        return Armies.illagerRaid(level, v.totem, target, troops);
    }

    /** One of an Illager war party, marching on {@code dest}. */
    public static @Nullable Mob spawnRaider(ServerLevel level, String troop, BlockPos at, BlockPos dest, RandomSource r) {
        EntityType<? extends Mob> type = switch (troop) {
            case "vindicator" -> EntityTypes.VINDICATOR;
            case "evoker" -> EntityTypes.EVOKER;
            case "witch" -> EntityTypes.WITCH;
            case "ravager" -> EntityTypes.RAVAGER;
            case "overlord" -> ModContent.ILLAGER_OVERLORD.get();
            default -> EntityTypes.PILLAGER;
        };
        Mob m = type.create(level, EntitySpawnReason.EVENT);
        if (m == null) return null;
        m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        if (m.getType() == EntityTypes.VINDICATOR) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        m.setPersistenceRequired();
        m.setHomeTo(dest, 10);
        enlist(m);
        level.addFreshEntity(m);
        if (m.getType() == EntityTypes.RAVAGER) {
            Mob rider = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
            if (rider != null) {
                rider.snapTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0);
                rider.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
                rider.setPersistenceRequired();
                enlist(rider);
                level.addFreshEntity(rider);
                rider.startRiding(m, true, true);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ conquest

    public static void overlordSlain(ServerLevel level, BlockPos at) {
        KingdomData d = Realm.data(level);
        for (KingdomData.OrcVillage v : d.illagerCamps) {
            if (v.dim.equals(KingdomData.dim(level)) && v.totem.distSqr(at) < 80 * 80) {
                v.warlordSlain = true;
                d.markChanged();
            }
        }
    }

    /** A player clicked an Illager Standard. */
    public static void tryClaim(ServerPlayer p, BlockPos standard) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        KingdomData.OrcVillage v = null;
        for (KingdomData.OrcVillage o : d.illagerCamps) if (o.dim.equals(KingdomData.dim(level)) && o.totem.distSqr(standard) < 9) v = o;
        if (v == null) {
            v = new KingdomData.OrcVillage(KingdomData.dim(level), standard.immutable(), false);
            d.illagerCamps.add(v);
        }
        if (v.conquered) {
            p.sendOverlayMessage(Component.literal("This camp has already been taken.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int left = members(level, standard).size();
        if (left > 0) {
            p.sendSystemMessage(Component.literal(left + " Illagers still hold the " + title(v) + ". Clear them out, then claim the Standard.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Kingdom mine = d.byPlayer(p.getUUID());
        if (mine == null) {
            p.sendSystemMessage(Realm.err("Found a kingdom first: then this land can be yours."));
            return;
        }
        ChunkPos hc = ChunkPos.containing(standard);
        int got = 0, rr = 1 + v.stage, coins = 30 + 35 * v.stage, emeralds = 5 + 6 * v.stage;
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) {
                ChunkPos cp = new ChunkPos(hc.x() + dx, hc.z() + dz);
                if (d.ownerId(level, cp) == null) {
                    d.claim(level, cp, mine);
                    got++;
                }
            }
        }
        v.conquered = true;
        // the captives join you, as long as there are beds for them
        int freed = 0, waiting = 0;
        for (Villager vil : level.getEntitiesOfClass(Villager.class, new AABB(standard).inflate(40, 16, 40), Villager::isAlive)) {
            if (vil.isBaby()) continue;
            if (mine.citizens.size() >= Realm.beds(level, mine)) {
                waiting++;
                continue;
            }
            Citizen c = ModContent.CITIZEN.get().create(level, EntitySpawnReason.CONVERSION);
            if (c == null) continue;
            c.snapTo(vil.getX(), vil.getY(), vil.getZ(), vil.getYRot(), 0);
            c.copyLookFrom(vil);
            c.joinKingdom(mine, level);
            level.addFreshEntity(c);
            vil.discard();
            freed++;
        }
        d.markChanged();
        Realm.give(p, new ItemStack(ModContent.GOLD_COIN.get(), coins));
        Realm.give(p, new ItemStack(Items.EMERALD, emeralds));
        level.playSound(null, standard, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3.0f, 1.3f);
        level.setBlock(standard, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        p.sendSystemMessage(Component.literal("The " + title(v) + " is yours: " + got + " chunks of land, " + coins + " gold coins and "
                + emeralds + " emeralds of plunder." + (freed > 0 ? " " + freed + " freed captive" + (freed == 1 ? "" : "s") + " swear fealty to you." : "")
                + (waiting > 0 ? " " + waiting + " more would join if you had the beds." : "")).withStyle(ChatFormatting.GOLD));
    }

    /** Debug: grow a camp straight to a citadel. */
    public static void fastForward(ServerLevel level, KingdomData.OrcVillage v) {
        v.days = Math.max(v.days, 8);
        grow(level, Realm.data(level), v);
    }
}
