package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.build.Structures;
import com.dominionmod.dominion.entity.Citizen;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Kingdoms in the wild have minds of their own. Every day each one grows a little (claims land,
 * raises a building, calls up soldiers) and looks at its neighbours: a strong, proud kingdom
 * demands tribute from a weak one or simply marches on it; a peaceable one sends envoys offering
 * trade, then alliance. At war, it sends armies (see {@link Armies}) until it is beaten or bought off.
 */
public final class Rivals {
    private Rivals() {}

    /** One wild kingdom's temperament and memory. */
    public static final class Rival {
        public final UUID kingdom;
        /** 0 = peaceable, 1 = warlike. */
        public float aggression;
        public long lastDay = -1;
        /** Soldiers it can call up who aren't standing in the world yet. */
        public int reserve;
        public int lostArmies;
        public long lastEnvoy = -10, lastArmy = -10;
        public boolean large;
        public int built;

        public Rival(UUID kingdom) {
            this.kingdom = kingdom;
        }

        private Rival(UUID kingdom, float aggression, long lastDay, int reserve, int lostArmies, long lastEnvoy, long lastArmy, boolean large,
                      int built) {
            this(kingdom);
            this.aggression = aggression;
            this.lastDay = lastDay;
            this.reserve = reserve;
            this.lostArmies = lostArmies;
            this.lastEnvoy = lastEnvoy;
            this.lastArmy = lastArmy;
            this.large = large;
            this.built = built;
        }

        public static final Codec<Rival> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(r -> r.kingdom),
                Codec.FLOAT.optionalFieldOf("aggression", 0.5f).forGetter(r -> r.aggression),
                Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(r -> r.lastDay),
                Codec.INT.optionalFieldOf("reserve", 0).forGetter(r -> r.reserve),
                Codec.INT.optionalFieldOf("lost_armies", 0).forGetter(r -> r.lostArmies),
                Codec.LONG.optionalFieldOf("last_envoy", -10L).forGetter(r -> r.lastEnvoy),
                Codec.LONG.optionalFieldOf("last_army", -10L).forGetter(r -> r.lastArmy),
                Codec.BOOL.optionalFieldOf("large", false).forGetter(r -> r.large),
                Codec.INT.optionalFieldOf("built", 0).forGetter(r -> r.built)
        ).apply(i, Rival::new));

        public String temper() {
            return aggression > 0.7f ? "warlike" : aggression > 0.45f ? "proud" : "peaceable";
        }
    }

    /** Called when a wild kingdom is founded. */
    public static void born(KingdomData d, Kingdom k, boolean large, RandomSource r) {
        Rival rv = d.rival(k);
        rv.large = large;
        rv.aggression = 0.15f + r.nextFloat() * 0.8f;
        rv.reserve = large ? 6 : 2;
        d.markChanged();
    }

    // ------------------------------------------------------------------ the daily round

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || level.getGameTime() % 200 != 37) return;
        KingdomData d = Realm.data(level);
        long day = Diplomacy.day(level);
        for (Kingdom k : new ArrayList<>(d.all())) {
            if (!k.wild || k.hall.isEmpty()) continue;
            Rival r = d.rival(k);
            if (r.lastDay < 0) r.lastDay = day;
            if (r.lastDay >= day) continue;
            r.lastDay = day;
            try {
                grow(level, d, k, r);
                consider(level, d, k, r, day);
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Rival kingdom {} stumbled", k.name, e);
            }
            d.markChanged();
        }
    }

    private static int claimCap(Rival r) {
        return r.large ? 49 : 25;
    }

    private static int levyCap(Rival r) {
        return r.large ? 16 : 7;
    }

    /** Is anyone close enough to the kingdom's hall for its buildings and people to be raised in the world? */
    public static boolean awake(ServerLevel level, Kingdom k) {
        if (k.hall.isEmpty() || !level.isLoaded(k.hall.get())) return false;
        BlockPos h = k.hall.get();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(h.getX(), p.getY(), h.getZ()) < 160 * 160) return true;
        return false;
    }

    public static void grow(ServerLevel level, KingdomData d, Kingdom k, Rival r) {
        RandomSource rnd = level.getRandom();
        String dim = KingdomData.dim(level);
        // land: one bordering chunk a day
        List<Long> mine = d.claimsOf(k, dim);
        if (mine.size() < claimCap(r) && !mine.isEmpty()) {
            for (int tries = 0; tries < 12; tries++) {
                ChunkPos from = ChunkPos.unpack(mine.get(rnd.nextInt(mine.size())));
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                int[] dir = dirs[rnd.nextInt(4)];
                ChunkPos to = new ChunkPos(from.x() + dir[0], from.z() + dir[1]);
                if (d.ownerId(level, to) == null) {
                    d.claim(level, to, k);
                    break;
                }
            }
        }
        // soldiers: called up a few at a time
        int soldiers = Diplomacy.strength(d, k);
        if (soldiers < levyCap(r)) r.reserve += r.large ? 2 : 1;
        if (!awake(level, k)) return;
        // a new building now and then, while someone is near enough to see it go up
        if (rnd.nextFloat() < 0.6f && r.built < (r.large ? 8 : 5)) {
            String id = pickBuilding(d, k, r, rnd);
            if (id != null && Settlements.raiseBuilding(level, k, id, r.large ? 46 : 30, rnd)) r.built++;
        }
        // muster a couple of the levies at the hall
        int muster = Math.min(r.reserve, 2);
        for (int i = 0; i < muster; i++) {
            if (Settlements.musterSoldier(level, k, rnd) != null) r.reserve--;
        }
    }

    private static @Nullable String pickBuilding(KingdomData d, Kingdom k, Rival r, RandomSource rnd) {
        int tier = d.progress(k).maxTier();
        List<String> options = new ArrayList<>(Structures.HOMES);
        options.add("farm");
        options.add("market_stall");
        if (tier >= 1) options.add("barracks");
        if (tier >= 1 && Structures.get("stables") != null) options.add("stables");
        if (tier >= 2) options.add("watchtower");
        return options.get(rnd.nextInt(options.size()));
    }

    /** Looks at each player kingdom nearby, and decides what to do about it. */
    private static void consider(ServerLevel level, KingdomData d, Kingdom k, Rival r, long day) {
        RandomSource rnd = level.getRandom();
        BlockPos home = k.hall.get();
        for (Kingdom pk : new ArrayList<>(d.all())) {
            if (pk.wild || pk.hall.isEmpty() || !pk.hallDim.equals(k.hallDim)) continue;
            double dist = Math.sqrt(pk.hall.get().distSqr(home));
            if (dist > 1400) continue;
            Diplomacy.Relation rel = Diplomacy.relation(d, k.id, pk.id);
            KingdomData.Pact pact = d.pact(k.id, pk.id);
            int mineStr = Diplomacy.strength(d, k), theirs = Math.max(1, Diplomacy.strength(d, pk));
            float ratio = mineStr / (float) theirs;
            if (rel == Diplomacy.Relation.WAR) {
                if (!Armies.marching(d, k, pk) && day - r.lastArmy >= 2 && r.reserve + mineStr >= 3) {
                    Armies.raise(level, k, pk);
                    r.lastArmy = day;
                } else if (r.lostArmies >= 2 && rnd.nextFloat() < 0.5f && day - r.lastEnvoy >= 1) {
                    envoy(level, k, pk, Diplomacy.newOffer(level, k, pk, Diplomacy.Kind.PEACE, 0));
                    r.lastEnvoy = day;
                }
                continue;
            }
            if (pact != null && day < pact.truceUntil) continue;
            if (day - r.lastEnvoy < 2) continue;
            float roll = rnd.nextFloat();
            if (r.aggression > 0.8f && ratio > 1.2f && roll < 0.2f) {
                Diplomacy.declareWar(level, k, pk, "their heralds ride out with no warning");
                r.lastEnvoy = day;
            } else if (r.aggression > 0.55f && ratio > 1.4f && roll < 0.5f) {
                int coins = Math.round(12 + 6 * Math.min(4, ratio));
                envoy(level, k, pk, Diplomacy.newOffer(level, k, pk, Diplomacy.Kind.TRIBUTE, coins));
                r.lastEnvoy = day;
            } else if (r.aggression < 0.45f && rel == Diplomacy.Relation.NEUTRAL && roll < 0.5f) {
                envoy(level, k, pk, Diplomacy.newOffer(level, k, pk, Diplomacy.Kind.TRADE, 0));
                r.lastEnvoy = day;
            } else if (r.aggression < 0.45f && rel == Diplomacy.Relation.TRADING && pact != null && day - pact.since >= 3 && roll < 0.4f) {
                envoy(level, k, pk, Diplomacy.newOffer(level, k, pk, Diplomacy.Kind.ALLIANCE, 0));
                r.lastEnvoy = day;
            }
        }
    }

    // ------------------------------------------------------------------ envoys

    /**
     * Sends an envoy carrying the kingdom's banner to the nearest member of the other kingdom who is
     * out in the world. With nobody about, a rider brings the message instead.
     */
    public static void envoy(ServerLevel level, Kingdom from, Kingdom to, Diplomacy.Offer offer) {
        ServerPlayer target = null;
        for (ServerPlayer p : level.players()) if (to.isMember(p.getUUID()) && p.isAlive()) target = p;
        if (target != null) {
            Citizen c = Settlements.spawnEnvoy(level, from, target, offer.id());
            if (c != null) {
                target.sendSystemMessage(Component.literal("An envoy bearing the banner of ").withStyle(ChatFormatting.GOLD).append(Realm.title(from))
                        .append(Component.literal(" rides toward you.").withStyle(ChatFormatting.GOLD)));
                return;
            }
        }
        Diplomacy.present(level, offer, "A rider brings word: ");
    }

    // ------------------------------------------------------------------ answering a player's proposals

    static Component answer(ServerPlayer p, Kingdom mine, Kingdom wild, String verb, Diplomacy.Relation now, int mineStr, int theirs) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Rival r = d.rival(wild);
        long day = Diplomacy.day(level);
        switch (verb) {
            case "peace" -> {
                if (now != Diplomacy.Relation.WAR) return Realm.err("You are not at war with " + wild.name + ".");
                if (r.lostArmies >= 1 || mineStr >= theirs * 1.2f) {
                    Diplomacy.makePeace(level, wild, mine, 5);
                    return Component.literal(wild.name + " is weary of war, and agrees.").withStyle(ChatFormatting.GREEN);
                }
                int coins = 15 + 5 * Math.max(0, theirs - mineStr);
                Diplomacy.present(level, Diplomacy.newOffer(level, wild, mine, Diplomacy.Kind.PEACE_FOR_GOLD, coins), "Their answer: ");
                return Component.literal(wild.name + " smells victory. They name their price.").withStyle(ChatFormatting.YELLOW);
            }
            case "trade" -> {
                if (now != Diplomacy.Relation.NEUTRAL) return Realm.err("You can only open trade with a kingdom you're at peace with and not already trading with.");
                if (r.aggression < 0.75f) {
                    Diplomacy.set(level, wild, mine, Diplomacy.Relation.TRADING);
                    return Component.literal(wild.name + " agrees to trade. Their caravans and yours will travel between your towns.")
                            .withStyle(ChatFormatting.GOLD);
                }
                return Component.literal(wild.name + " (" + r.temper() + ") wants nothing to do with your merchants.").withStyle(ChatFormatting.GRAY);
            }
            case "alliance" -> {
                KingdomData.Pact pact = d.pact(mine.id, wild.id);
                if (now != Diplomacy.Relation.TRADING || pact == null) return Realm.err("Trade with them first: alliances grow out of friendship.");
                if (day - pact.since < 2) return Component.literal(wild.name + " wants to trade with you a while longer first.").withStyle(ChatFormatting.GRAY);
                if (r.aggression >= 0.55f) return Component.literal(wild.name + " (" + r.temper() + ") will bow to no alliance.").withStyle(ChatFormatting.GRAY);
                if (mineStr * 2 < theirs) return Component.literal(wild.name + " sees no use in an ally so weak.").withStyle(ChatFormatting.GRAY);
                Diplomacy.set(level, wild, mine, Diplomacy.Relation.ALLIED);
                return Component.literal(wild.name + " swears alliance with you!").withStyle(ChatFormatting.GREEN);
            }
            case "tribute" -> {
                if (now == Diplomacy.Relation.WAR) return Realm.err("You are at war: offer peace instead, or beat them.");
                if (mineStr >= 4 && mineStr >= theirs * 2) {
                    int coins = 10 + 3 * d.progress(wild).buildings.size();
                    Diplomacy.receive(level, mine, coins);
                    KingdomData.Pact pact = d.pact(mine.id, wild.id);
                    if (pact == null) Diplomacy.set(level, mine, wild, now);
                    pact = d.pact(mine.id, wild.id);
                    if (pact != null) pact.truceUntil = day + 3;
                    r.aggression = Math.min(1f, r.aggression + 0.1f);
                    return Component.literal(wild.name + " bows before your strength and pays " + coins + " Gold Coins.").withStyle(ChatFormatting.GOLD);
                }
                r.aggression = Math.min(1f, r.aggression + 0.15f);
                if (level.getRandom().nextFloat() < 0.5f + r.aggression * 0.4f) {
                    Diplomacy.declareWar(level, wild, mine, "they will not be threatened");
                    onWarDeclared(level, wild, mine);
                    return Component.literal(wild.name + " laughs at your demand, and takes up arms!").withStyle(ChatFormatting.RED);
                }
                return Component.literal(wild.name + " refuses, and remembers the insult.").withStyle(ChatFormatting.GRAY);
            }
            default -> {
                return Realm.err("Unknown action.");
            }
        }
    }

    /** A wild kingdom finds itself at war: its army sets out soon. */
    public static void onWarDeclared(ServerLevel level, Kingdom wild, Kingdom enemy) {
        if (!wild.wild || enemy.wild) return;
        KingdomData d = Realm.data(level);
        Rival r = d.rival(wild);
        if (!Armies.marching(d, wild, enemy) && r.reserve + Diplomacy.strength(d, wild) >= 3) {
            Armies.raise(level, wild, enemy);
            r.lastArmy = Diplomacy.day(level);
        }
    }
}
