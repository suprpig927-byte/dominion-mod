package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.build.Plans;
import com.dominionmod.dominion.build.Structures;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Watchtowers come in three levels: the log Lookout, the Stone Watchtower and the Fortified Tower.
 * Each keeps a garrison of archers on its top (1, 2 or 4) who shoot further and harder from up
 * there; spots enemies within 40, 56 or 72 blocks, makes them glow and rings the bell; sees armies
 * on the march from much further off; and lets the kingdom claim land up to 1, 2 or 3 chunks round
 * it, even beyond its borders. Sneak + right-click a tower's sign to raise it to the next level.
 */
public final class Watchtowers {
    private Watchtowers() {}

    public static final List<String> LEVELS = List.of("lookout", "watchtower", "fortified_tower");
    public static final String[] RESEARCH = {null, "stone", "fortification"};
    private static final int[] GARRISON = {1, 2, 4}, SIGHT = {40, 56, 72}, CLAIM = {1, 2, 3};
    /** Where the garrison stands, as (x, layer, z) cells of each design. */
    private static final int[][][] SPOTS = {
            {{1, 7, 1}, {3, 7, 3}, {3, 7, 1}, {1, 7, 3}},
            {{1, 8, 1}, {3, 8, 3}, {3, 8, 1}, {1, 8, 3}},
            {{1, 8, 1}, {5, 8, 5}, {5, 8, 1}, {1, 8, 5}}};

    /** Archer -> the spot they're posted to. Rebuilt from the towers every couple of seconds; not saved. */
    private static final Map<UUID, BlockPos> POSTS = new HashMap<>();
    /** Tower -> the next game time it may ring an alarm. */
    private static final Map<UUID, Long> QUIET_UNTIL = new HashMap<>();
    /** Armies each kingdom's towers have already reported. */
    private static final Map<UUID, Set<UUID>> SPOTTED = new HashMap<>();

    public static int level(Building b) {
        return LEVELS.indexOf(b.structure) + 1;
    }

    public static int garrisonSize(Building b) { return GARRISON[Math.max(0, level(b) - 1)]; }
    public static int sight(Building b) { return SIGHT[Math.max(0, level(b) - 1)]; }
    public static int claimRadius(Building b) { return CLAIM[Math.max(0, level(b) - 1)]; }

    public static @Nullable String next(Building b) {
        int l = level(b);
        return l <= 0 || l >= LEVELS.size() ? null : LEVELS.get(l);
    }

    public static boolean garrisoned(Citizen c) {
        return POSTS.containsKey(c.getUUID());
    }

    public static @Nullable BlockPos postOf(Citizen c) {
        return POSTS.get(c.getUUID());
    }

    private static BlockPos spot(Building b, int i) {
        Structures.Structure s = Structures.get(b.structure);
        int[] cell = SPOTS[level(b) - 1][i % 4];
        return Plans.cell(s, b.origin, b.rotation, cell[0], cell[1], cell[2]);
    }

    /** The top of the tower (where the bell hangs). */
    public static BlockPos top(Building b) {
        Structures.Structure s = Structures.get(b.structure);
        return Plans.cell(s, b.origin, b.rotation, s.width() / 2, s.height() - 2, s.depth() / 2);
    }

    // ------------------------------------------------------------------ the watch

    public static void tick(ServerLevel level) {
        long t = level.getGameTime();
        if (t % 40 != 17) return;
        KingdomData d = Realm.data(level);
        String dim = KingdomData.dim(level);
        POSTS.entrySet().removeIf(e -> !(level.getEntity(e.getKey()) instanceof Citizen c) || !c.isAlive());
        for (Kingdom k : new ArrayList<>(d.all())) {
            if (k.wild) continue;
            for (Building b : d.progress(k).buildings) {
                if (level(b) == 0 || !b.dim.equals(dim) || !level.isLoaded(b.origin)) continue;
                try {
                    garrison(level, d, k, b);
                    watch(level, d, k, b, t);
                } catch (RuntimeException e) {
                    DominionMod.LOGGER.error("Watchtower at {} misbehaved", b.origin, e);
                }
            }
        }
    }

    private static boolean canServe(Citizen c, Kingdom k) {
        return c.isAlive() && c.sameKingdom(k.id) && c.job() == Job.ARCHER && c.order() == Order.DUTY && !c.isPassenger() && !c.temporary()
                && !c.isChild();
    }

    private static void garrison(ServerLevel level, KingdomData d, Kingdom k, Building b) {
        for (UUID id : new ArrayList<>(b.workers)) {
            Entity e = level.getEntity(id);
            if (e == null) {
                if (!k.citizens.containsKey(id)) b.workers.remove(id);
                continue;
            }
            if (!(e instanceof Citizen c) || !canServe(c, k)) {
                b.workers.remove(id);
                POSTS.remove(id);
                if (e instanceof Citizen c && c.isAlive()) {
                    BlockPos foot = b.center();
                    if (c.distanceToSqr(foot.getX(), c.getY(), foot.getZ()) < 36 && c.getY() > foot.getY() + 3) c.teleportTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 2.5);
                }
                d.markChanged();
            }
        }
        int want = garrisonSize(b);
        if (b.workers.size() < want) {
            BlockPos at = b.center();
            Set<UUID> taken = new HashSet<>(POSTS.keySet());
            for (Building o : d.progress(k).buildings) taken.addAll(o.workers);
            List<Citizen> free = level.getEntitiesOfClass(Citizen.class, new AABB(at).inflate(64, 24, 64), c -> canServe(c, k) && !taken.contains(c.getUUID()));
            free.sort((x, y) -> Double.compare(x.distanceToSqr(at.getX(), at.getY(), at.getZ()), y.distanceToSqr(at.getX(), at.getY(), at.getZ())));
            for (Citizen c : free) {
                if (b.workers.size() >= want) break;
                b.workers.add(c.getUUID());
                d.markChanged();
                Citizen.tellKingdom(level, k, Component.literal(c.baseName() + " climbs the " + b.name() + " to keep watch.").withStyle(ChatFormatting.GRAY));
            }
        }
        for (int i = 0; i < b.workers.size(); i++) POSTS.put(b.workers.get(i), spot(b, i));
    }

    /** Anyone the kingdom should be warned about. */
    public static boolean hostileTo(ServerLevel level, Kingdom k, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (e instanceof Citizen c) return c.kingdomId() != null && Diplomacy.hostile(level.getServer(), k.id, c.kingdomId());
        if (e instanceof Player p) {
            if (p.isSpectator() || p.isCreative()) return false;
            Kingdom pk = Realm.data(level).byPlayer(p.getUUID());
            return pk != null && Diplomacy.hostile(level.getServer(), k.id, pk.id);
        }
        if (e instanceof com.dominionmod.dominion.entity.siege.SiegeEngine se)
            return se.kingdomId() != null && Diplomacy.hostile(level.getServer(), k.id, se.kingdomId());
        return e instanceof Enemy && e instanceof Mob;
    }

    private static void watch(ServerLevel level, KingdomData d, Kingdom k, Building b, long t) {
        int r = sight(b);
        BlockPos at = b.center();
        List<LivingEntity> seen = level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(r, 32, r),
                e -> e.distanceToSqr(at.getX(), e.getY(), at.getZ()) < r * r && hostileTo(level, k, e));
        int raiders = 0, soldiers = 0, monsters = 0;
        for (LivingEntity e : seen) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false));
            if (IllagerEmpire.isIllager(e) || e instanceof com.dominionmod.dominion.entity.wild.Orc) raiders++;
            else if (e instanceof Citizen || e instanceof Player || e instanceof com.dominionmod.dominion.entity.siege.SiegeEngine) soldiers++;
            else monsters++;
        }
        boolean alarm = raiders + soldiers > 0 || monsters >= 4;
        if (alarm && t >= QUIET_UNTIL.getOrDefault(b.id, 0L)) {
            QUIET_UNTIL.put(b.id, t + 1800);
            BlockPos top = top(b);
            for (int i = 0; i < 3; i++) level.playSound(null, top, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.0f - i * 0.05f);
            LivingEntity first = seen.get(0);
            String what = soldiers > 0 ? soldiers + " enemy soldier" + (soldiers == 1 ? "" : "s")
                    : raiders > 0 ? raiders + (first instanceof com.dominionmod.dominion.entity.wild.Orc ? " orc" : " Illager") + (raiders == 1 ? "" : "s")
                    : monsters + " monsters";
            int dist = (int) Math.sqrt(first.distanceToSqr(at.getX(), first.getY(), at.getZ()));
            Citizen.tellKingdom(level, k, Component.literal("The bell rings! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("The " + b.name() + " at " + at.getX() + ", " + at.getZ() + " spots " + what + ", " + dist
                            + " blocks to the " + Armies.compass(at, first.blockPosition()) + ".").withStyle(ChatFormatting.YELLOW)));
        }
        // armies and war parties on the march toward us, seen from far off
        Set<UUID> told = SPOTTED.computeIfAbsent(k.id, x -> new HashSet<>());
        for (Armies.Army a : d.armies) {
            if (!a.target.equals(k.id) || a.returning || told.contains(a.id) || !a.dim.equals(b.dim)) continue;
            double dist = Math.hypot(a.x - at.getX(), a.z - at.getZ());
            if (dist > r * 3) continue;
            told.add(a.id);
            Kingdom from = d.byId(a.kingdom);
            String who = a.raiders() ? a.who() : "the army of " + (from == null ? "an enemy" : from.name);
            level.playSound(null, top(b), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 0.8f);
            Citizen.tellKingdom(level, k, Component.literal("From the " + b.name() + ": ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("lookouts see " + who + " (" + a.troops.size() + " strong) " + (int) dist + " blocks to the "
                            + Armies.compass(at, BlockPos.containing(a.x, at.getY(), a.z)) + ", marching this way.").withStyle(ChatFormatting.YELLOW)));
        }
    }

    // ------------------------------------------------------------------ land

    /** Can the kingdom claim this chunk because one of its towers watches over it? */
    public static boolean covers(ServerLevel level, KingdomData d, Kingdom k, ChunkPos chunk) {
        String dim = KingdomData.dim(level);
        for (Building b : d.progress(k).buildings) {
            if (level(b) == 0 || !b.dim.equals(dim)) continue;
            ChunkPos c = ChunkPos.containing(b.origin);
            if (Math.max(Math.abs(c.x() - chunk.x()), Math.abs(c.z() - chunk.z())) <= claimRadius(b)) return true;
        }
        return false;
    }

    /** For the Defense tab: tower|name|x|z|level|garrison names|max|sight|claim */
    public static List<String> rows(ServerLevel level, Kingdom k) {
        List<String> out = new ArrayList<>();
        KingdomData d = Realm.data(level);
        for (Building b : d.progress(k).buildings) {
            if (level(b) == 0) continue;
            List<String> names = new ArrayList<>();
            for (UUID id : b.workers) names.add(k.names.getOrDefault(id, "?"));
            out.add("tower|" + b.name() + "|" + b.origin.getX() + "|" + b.origin.getZ() + "|" + level(b) + "|" + String.join(", ", names) + "|"
                    + garrisonSize(b) + "|" + sight(b) + "|" + claimRadius(b));
        }
        for (Armies.Army a : d.armies) {
            if (!a.target.equals(k.id) || a.returning || k.hall.isEmpty()) continue;
            Kingdom from = d.byId(a.kingdom);
            String who = a.raiders() ? a.who() : "Army of " + (from == null ? "?" : from.name);
            int dist = (int) Math.hypot(a.x - k.hall.get().getX(), a.z - k.hall.get().getZ());
            out.add("threat|" + who + "|" + dist + "|" + Armies.compass(k.hall.get(), BlockPos.containing(a.x, 0, a.z)) + "|" + a.troops.size() + "|"
                    + (a.materialized ? "in sight" : "marching"));
        }
        BlockPos home = k.hall.orElse(null);
        if (home != null) {
            for (KingdomData.OrcVillage v : d.orcVillages) {
                if (v.conquered || !v.dim.equals(k.hallDim)) continue;
                int dist = (int) Math.sqrt(v.totem.distSqr(home));
                if (dist < 1200) out.add("camp|" + v.title() + "|" + dist + "|" + Armies.compass(home, v.totem) + "|" + v.stage);
            }
            for (KingdomData.OrcVillage v : d.illagerCamps) {
                if (v.conquered || !v.dim.equals(k.hallDim)) continue;
                int dist = (int) Math.sqrt(v.totem.distSqr(home));
                if (dist < 1200) out.add("camp|" + IllagerEmpire.title(v) + "|" + dist + "|" + Armies.compass(home, v.totem) + "|" + v.stage);
            }
        }
        return out;
    }
}
