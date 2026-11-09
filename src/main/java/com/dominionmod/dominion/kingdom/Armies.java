package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.wild.Orc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Armies and raiding parties on the march. Far from any player an army is just a marker moving
 * across the map at marching pace; when someone comes within sight of it (or it reaches a town
 * that someone is guarding) it takes shape as real soldiers, horses and siege engines who march on,
 * fight, and either plunder the town or die trying. Survivors go home.
 */
public final class Armies {
    private Armies() {}

    /** The "kingdom" orc warbands march for. */
    public static final UUID ORCS = new UUID(0x0BC5L, 0x0BC5L);

    public static final class Army {
        public final UUID id;
        public final UUID kingdom, target;
        public final String dim;
        public double x, z;
        public final BlockPos dest, home;
        public final List<String> troops = new ArrayList<>();
        public boolean materialized, returning;
        public final List<UUID> members = new ArrayList<>();
        /** "war" (a kingdom's army) or "raid" (an orc warband). */
        public final String kind;
        public int quiet;

        public Army(UUID id, UUID kingdom, UUID target, String dim, double x, double z, BlockPos dest, BlockPos home, List<String> troops,
                    boolean materialized, boolean returning, List<UUID> members, String kind) {
            this.id = id;
            this.kingdom = kingdom;
            this.target = target;
            this.dim = dim;
            this.x = x;
            this.z = z;
            this.dest = dest;
            this.home = home;
            this.troops.addAll(troops);
            this.materialized = materialized;
            this.returning = returning;
            this.members.addAll(members);
            this.kind = kind;
        }

        public static final Codec<Army> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(a -> a.id),
                UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(a -> a.kingdom),
                UUIDUtil.STRING_CODEC.fieldOf("target").forGetter(a -> a.target),
                Codec.STRING.fieldOf("dim").forGetter(a -> a.dim),
                Codec.DOUBLE.fieldOf("x").forGetter(a -> a.x),
                Codec.DOUBLE.fieldOf("z").forGetter(a -> a.z),
                BlockPos.CODEC.fieldOf("dest").forGetter(a -> a.dest),
                BlockPos.CODEC.fieldOf("home").forGetter(a -> a.home),
                Codec.STRING.listOf().fieldOf("troops").forGetter(a -> a.troops),
                Codec.BOOL.optionalFieldOf("materialized", false).forGetter(a -> a.materialized),
                Codec.BOOL.optionalFieldOf("returning", false).forGetter(a -> a.returning),
                UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(a -> a.members),
                Codec.STRING.optionalFieldOf("kind", "war").forGetter(a -> a.kind)
        ).apply(i, Army::new));

        public boolean orcs() { return "raid".equals(kind); }

        public Vec3 at() { return new Vec3(x, 0, z); }
    }

    /** Marching pace while unseen, in blocks a second. */
    private static final double PACE = 3.0;

    /** Is an enemy army or warband in the field near this kingdom's Town Hall right now? */
    public static boolean underAttack(ServerLevel level, Kingdom k) {
        if (k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(level))) return false;
        for (Army a : Realm.data(level).armies) {
            if (!a.target.equals(k.id) || !a.materialized || a.returning) continue;
            if (Math.hypot(a.x - k.hall.get().getX(), a.z - k.hall.get().getZ()) < 110) return true;
        }
        return false;
    }

    public static boolean marching(KingdomData d, Kingdom from, Kingdom to) {
        for (Army a : d.armies) if (a.kingdom.equals(from.id) && a.target.equals(to.id) && !a.returning) return true;
        return false;
    }

    // ------------------------------------------------------------------ raising

    /** A wild kingdom sends an army against another kingdom's Town Hall. */
    public static @Nullable Army raise(ServerLevel level, Kingdom from, Kingdom to) {
        if (from.hall.isEmpty() || to.hall.isEmpty() || !from.hallDim.equals(to.hallDim)) return null;
        KingdomData d = Realm.data(level);
        Rivals.Rival r = d.rival(from);
        int tier = d.progress(from).maxTier();
        int cap = r.large ? 12 : 7;
        int n = Math.max(3, Math.min(cap, r.reserve + 2));
        r.reserve = Math.max(0, r.reserve - n);
        List<String> troops = new ArrayList<>();
        int knights = tier >= 2 ? n / 4 : 0, archers = Math.max(1, n / 3), muskets = tier >= 3 ? 1 + n / 6 : 0;
        int horseArchers = tier >= 2 && r.large ? 1 : 0;
        for (int i = 0; i < knights; i++) troops.add("knight");
        for (int i = 0; i < archers - horseArchers; i++) troops.add("archer");
        for (int i = 0; i < horseArchers; i++) troops.add("horse_archer");
        for (int i = 0; i < muskets; i++) troops.add("musketeer");
        while (troops.size() < n) troops.add(troops.size() % 3 == 0 ? "pikeman" : "militia");
        if (tier >= 1) troops.add("ram");
        if (r.large && tier >= 2) troops.add("trebuchet");
        if (r.large && tier >= 3) troops.add("cannon");
        BlockPos home = from.hall.get(), dest = to.hall.get();
        Army a = new Army(UUID.randomUUID(), from.id, to.id, from.hallDim, home.getX() + 0.5, home.getZ() + 0.5, dest, home, troops,
                false, false, List.of(), "war");
        d.armies.add(a);
        d.markChanged();
        int dist = (int) Math.sqrt(home.distSqr(dest));
        Citizen.tellKingdom(level, to, Component.literal("War drums! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("An army of " + n + " from ").withStyle(ChatFormatting.RED)).append(Realm.title(from))
                .append(Component.literal(" marches on your Town Hall from the " + compass(dest, home) + ", " + dist + " blocks away"
                        + describeSiege(troops) + ". It will arrive in about " + Math.max(1, Math.round(dist / PACE / 60)) + " minutes.")
                        .withStyle(ChatFormatting.RED)));
        return a;
    }

    /** An orc warband sets out from its stronghold to raid a town by night. */
    public static Army raid(ServerLevel level, BlockPos from, Kingdom to, int orcs, boolean warlord) {
        KingdomData d = Realm.data(level);
        List<String> troops = new ArrayList<>();
        troops.add(warlord ? "warlord" : "orc_chief");
        for (int i = 0; i < orcs; i++) troops.add("orc");
        Army a = new Army(UUID.randomUUID(), ORCS, to.id, to.hallDim, from.getX() + 0.5, from.getZ() + 0.5, to.hall.orElse(from), from, troops,
                false, false, List.of(), "raid");
        d.armies.add(a);
        d.markChanged();
        Citizen.tellKingdom(level, to, Component.literal("Orc war-drums in the night! ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                .append(Component.literal("A warband of " + troops.size() + (warlord ? ", led by their Warlord," : "") + " comes from the "
                        + compass(a.dest, from) + " to raid your town.").withStyle(ChatFormatting.GREEN)));
        return a;
    }

    private static String describeSiege(List<String> troops) {
        List<String> engines = new ArrayList<>();
        if (troops.contains("ram")) engines.add("a battering ram");
        if (troops.contains("trebuchet")) engines.add("a trebuchet");
        if (troops.contains("cannon")) engines.add("a cannon");
        return engines.isEmpty() ? "" : ", bringing " + String.join(" and ", engines);
    }

    static String compass(BlockPos to, BlockPos from) {
        double ang = Math.toDegrees(Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()));
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return names[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
    }

    /** Peace was made: armies between these two turn for home. */
    public static void recall(ServerLevel level, Kingdom a, Kingdom b) {
        for (Army army : Realm.data(level).armies) {
            if ((army.kingdom.equals(a.id) && army.target.equals(b.id)) || (army.kingdom.equals(b.id) && army.target.equals(a.id))) goHome(level, army);
        }
    }

    // ------------------------------------------------------------------ the march

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 3) return;
        KingdomData d = Realm.data(level);
        String dim = KingdomData.dim(level);
        for (Army a : new ArrayList<>(d.armies)) {
            if (!a.dim.equals(dim)) continue;
            try {
                step(level, d, a);
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Army {} lost its way", a.id, e);
                d.armies.remove(a);
            }
        }
    }

    /** Test hook: runs a number of march steps at once. */
    public static void debugMarch(ServerLevel level, Army a, int steps) {
        KingdomData d = Realm.data(level);
        for (int i = 0; i < steps && d.armies.contains(a); i++) step(level, d, a);
    }

    private static void step(ServerLevel level, KingdomData d, Army a) {
        Kingdom target = d.byId(a.target);
        if (!a.orcs() && d.byId(a.kingdom) == null || target == null) {
            disband(level, d, a);
            return;
        }
        if (!a.materialized) {
            BlockPos goal = a.returning ? a.home : a.dest;
            double dx = goal.getX() + 0.5 - a.x, dz = goal.getZ() + 0.5 - a.z, dist = Math.sqrt(dx * dx + dz * dz);
            if (a.returning) {
                if (dist < 8) disband(level, d, a);
                else move(a, dx, dz, dist, 0);
                return;
            }
            if (a.orcs() && level.isBrightOutside()) {
                disband(level, d, a); // dawn: the warband slinks home unseen
                return;
            }
            if (dist > 30) move(a, dx, dz, dist, 30);
            BlockPos here = BlockPos.containing(a.x, 0, a.z);
            boolean seen = level.hasChunk(here.getX() >> 4, here.getZ() >> 4) && nearestPlayer(level, a.x, a.z, 112) != null;
            if (seen) materialize(level, d, a, target);
            d.markChanged();
            return;
        }
        // in the world: count who's left, and see how the fight goes
        List<LivingEntity> alive = new ArrayList<>();
        for (UUID id : a.members) if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) alive.add(e);
        if (a.members.isEmpty()) {
            if (!a.returning) defeated(level, d, a, target);
            d.armies.remove(a);
            d.markChanged();
            return;
        }
        if (alive.isEmpty()) return; // all of them in unloaded chunks: wait
        double cx = 0, cz = 0;
        for (LivingEntity e : alive) {
            cx += e.getX();
            cz += e.getZ();
        }
        a.x = cx / alive.size();
        a.z = cz / alive.size();
        if (a.returning) {
            // once out of everyone's sight, the survivors are home
            for (LivingEntity e : alive) {
                if (nearestPlayer(level, e.getX(), e.getZ(), 80) == null) {
                    a.members.remove(e.getUUID());
                    if (e instanceof Citizen c) {
                        Kingdom k = d.byId(a.kingdom);
                        if (k != null && k.wild) d.rival(k).reserve++;
                        Realm.removeCitizen(level, a.kingdom, c.getUUID());
                    }
                    e.discard();
                }
            }
            d.markChanged();
            return;
        }
        // orcs don't stay out in daylight
        if (a.orcs() && level.isBrightOutside()) {
            Citizen.tellKingdom(level, target, Component.literal("The orcs fall back as the sun comes up.").withStyle(ChatFormatting.DARK_GREEN));
            goHome(level, a);
            return;
        }
        // keep everyone pointed at the town
        for (LivingEntity e : alive) {
            if (e instanceof Citizen c && c.order() != com.dominionmod.dominion.entity.Order.MARCH) c.march(a.dest);
            if (e instanceof Orc o) o.setHomeTo(a.dest, 10);
        }
        if (Math.hypot(a.x - a.dest.getX(), a.z - a.dest.getZ()) < 26) {
            if (defenders(level, target, a.dest) == 0) a.quiet++;
            else a.quiet = 0;
            if (a.quiet >= 5) victory(level, d, a, target);
        }
    }

    private static void move(Army a, double dx, double dz, double dist, double stopShort) {
        double stepLen = Math.min(PACE, Math.max(0, dist - stopShort));
        a.x += dx / dist * stepLen;
        a.z += dz / dist * stepLen;
    }

    private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double dd = (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z);
            if (dd < bd) {
                bd = dd;
                best = p;
            }
        }
        return best;
    }

    /** Defending soldiers and members near the Town Hall. */
    public static int defenders(ServerLevel level, Kingdom k, BlockPos hall) {
        var box = new net.minecraft.world.phys.AABB(hall).inflate(40, 16, 40);
        int n = level.getEntitiesOfClass(Citizen.class, box, c -> c.isAlive() && c.job().soldier
                && (c.sameKingdom(k.id) || Diplomacy.friendly(level.getServer(), c.kingdomId(), k.id))).size();
        for (ServerPlayer p : level.players()) if (k.isMember(p.getUUID()) && p.isAlive() && !p.isSpectator() && box.contains(p.position())) n++;
        return n;
    }

    // ------------------------------------------------------------------ taking shape

    private static void materialize(ServerLevel level, KingdomData d, Army a, Kingdom target) {
        Kingdom k = d.byId(a.kingdom);
        RandomSource r = level.getRandom();
        BlockPos center = BlockPos.containing(a.x, 0, a.z);
        a.materialized = true;
        double fx = a.dest.getX() - a.x, fz = a.dest.getZ() - a.z, fl = Math.max(1, Math.hypot(fx, fz));
        int i = 0;
        for (String troop : a.troops) {
            // ranks of three, the siege train behind
            boolean engine = troop.equals("ram") || troop.equals("trebuchet") || troop.equals("cannon");
            double back = engine ? 6 + (i % 3) * 3 : (i / 3) * 2.2, side = ((i % 3) - 1) * 2.2;
            double px = a.x - fx / fl * back - fz / fl * side, pz = a.z - fz / fl * back + fx / fl * side;
            BlockPos ground = new BlockPos((int) Math.floor(px), 0, (int) Math.floor(pz));
            level.getChunk(ground.getX() >> 4, ground.getZ() >> 4);
            BlockPos at = new BlockPos(ground.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.getX(), ground.getZ()), ground.getZ());
            Entity e = a.orcs() ? Settlements.spawnRaider(level, troop, at, a.dest, r)
                    : k == null ? null : Settlements.spawnTrooper(level, k, troop, at, a.dest, r);
            if (e != null) {
                a.members.add(e.getUUID());
                if (e.getVehicle() != null) a.members.add(e.getVehicle().getUUID());
            }
            i++;
        }
        d.markChanged();
        Component who = a.orcs() ? Component.literal("The orc warband").withStyle(ChatFormatting.DARK_GREEN) : Component.literal("The army of ")
                .withStyle(ChatFormatting.RED).append(Realm.title(k));
        Citizen.tellKingdom(level, target, Component.empty().append(who).append(Component.literal(" is in sight, " +
                (int) Math.hypot(a.x - a.dest.getX(), a.z - a.dest.getZ()) + " blocks to the " + compass(a.dest, center) + "!").withStyle(ChatFormatting.RED)));
        level.playSound(null, center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0f, a.orcs() ? 0.6f : 0.9f);
    }

    /** A member of an army died (or a siege engine was wrecked). */
    public static void onDeath(ServerLevel level, UUID id) {
        KingdomData d = Realm.data(level);
        for (Army a : d.armies) {
            if (a.members.remove(id)) {
                d.markChanged();
                return;
            }
        }
    }

    public static @Nullable Army armyOf(KingdomData d, UUID member) {
        for (Army a : d.armies) if (a.members.contains(member)) return a;
        return null;
    }

    // ------------------------------------------------------------------ outcomes

    private static void victory(ServerLevel level, KingdomData d, Army a, Kingdom target) {
        var store = Storehouse.containers(level, target);
        int coins = Storehouse.count(store, s -> s.is(com.dominionmod.dominion.registry.ModContent.GOLD_COIN.get()));
        int take = Math.min(60, coins / 2);
        Storehouse.take(store, s -> s.is(com.dominionmod.dominion.registry.ModContent.GOLD_COIN.get()), take);
        int food = Storehouse.take(store, s -> s.has(net.minecraft.core.component.DataComponents.FOOD), 24).getCount();
        Kingdom k = d.byId(a.kingdom);
        if (k != null) Diplomacy.receive(level, k, take);
        String who = a.orcs() ? "The orcs" : "The army of " + (k == null ? "the enemy" : k.name);
        Citizen.tellKingdom(level, target, Component.literal(who + " stands unopposed at your Town Hall. They carry off " + take
                + " Gold Coins and " + food + " food, and march home.").withStyle(ChatFormatting.DARK_RED));
        goHome(level, a);
    }

    private static void defeated(ServerLevel level, KingdomData d, Army a, Kingdom target) {
        Kingdom k = d.byId(a.kingdom);
        if (k != null && k.wild) d.rival(k).lostArmies++;
        String who = a.orcs() ? "the orc warband" : "the army of " + (k == null ? "the enemy" : k.name);
        Citizen.tellKingdom(level, target, Component.literal("Victory! You routed " + who + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        level.playSound(null, a.dest, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void goHome(ServerLevel level, Army a) {
        a.returning = true;
        for (UUID id : a.members) {
            Entity e = level.getEntity(id);
            if (e instanceof Citizen c) c.march(a.home);
            if (e instanceof Orc o) o.setHomeTo(a.home, 24);
        }
        Realm.data(level).markChanged();
    }

    private static void disband(ServerLevel level, KingdomData d, Army a) {
        Kingdom k = d.byId(a.kingdom);
        if (k != null && k.wild) {
            int soldiers = 0;
            for (String t : a.troops) if (!t.equals("ram") && !t.equals("trebuchet") && !t.equals("cannon")) soldiers++;
            d.rival(k).reserve += a.materialized ? 0 : soldiers;
        }
        for (UUID id : a.members) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        d.armies.remove(a);
        d.markChanged();
    }

    /** Debug and tests: every army's whereabouts. */
    public static List<String> describe(ServerLevel level) {
        List<String> out = new ArrayList<>();
        KingdomData d = Realm.data(level);
        for (Army a : d.armies) {
            Kingdom k = d.byId(a.kingdom);
            out.add((a.orcs() ? "orcs" : k == null ? "?" : k.name) + " -> " + (d.byId(a.target) == null ? "?" : d.byId(a.target).name)
                    + String.format(" at %.0f,%.0f", a.x, a.z) + (a.materialized ? " (in the field, " + a.members.size() + " left)" : " (marching)")
                    + (a.returning ? " returning" : "") + " " + a.troops);
        }
        return out;
    }

    static boolean isOverworld(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }
}
