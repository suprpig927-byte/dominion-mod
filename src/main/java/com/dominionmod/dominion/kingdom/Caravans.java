package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.block.MarketBlock;
import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.SupplyCart;
import com.dominionmod.dominion.entity.wild.BreedHorse;
import com.dominionmod.dominion.registry.ModContent;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Trade caravans: a merchant leading a pony and cart, with whatever guards the kingdom hired at its
 * market. A kingdom with a Market Stall sends one down each trade route (to every kingdom it trades
 * or is allied with, and to each town it has conquered) and the caravan comes back with coins for
 * the goods it sold. Your trading partners send caravans to your Town Hall too, and pay a toll.
 *
 * <p>Out on the open road bandits lie in wait. Roads make the going faster and safer, and every
 * guard halves the risk. Unseen, a caravan is only a marker moving along its route; when a player
 * is near, it is there in the flesh, and so are any bandits who come for it.
 */
public final class Caravans {
    private Caravans() {}

    public static final class Caravan {
        public final UUID id, kingdom, partner;
        public final String dim;
        public double x, z;
        public final BlockPos from, to;
        public boolean homeward;
        public int goods, guards;
        public boolean materialized;
        public final List<UUID> members = new ArrayList<>();

        public Caravan(UUID id, UUID kingdom, UUID partner, String dim, double x, double z, BlockPos from, BlockPos to, boolean homeward, int goods,
                       int guards, boolean materialized, List<UUID> members) {
            this.id = id;
            this.kingdom = kingdom;
            this.partner = partner;
            this.dim = dim;
            this.x = x;
            this.z = z;
            this.from = from;
            this.to = to;
            this.homeward = homeward;
            this.goods = goods;
            this.guards = guards;
            this.materialized = materialized;
            this.members.addAll(members);
        }

        public static final Codec<Caravan> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(c -> c.id),
                UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(c -> c.kingdom),
                UUIDUtil.STRING_CODEC.fieldOf("partner").forGetter(c -> c.partner),
                Codec.STRING.fieldOf("dim").forGetter(c -> c.dim),
                Codec.DOUBLE.fieldOf("x").forGetter(c -> c.x),
                Codec.DOUBLE.fieldOf("z").forGetter(c -> c.z),
                BlockPos.CODEC.fieldOf("from").forGetter(c -> c.from),
                BlockPos.CODEC.fieldOf("to").forGetter(c -> c.to),
                Codec.BOOL.optionalFieldOf("homeward", false).forGetter(c -> c.homeward),
                Codec.INT.optionalFieldOf("goods", 0).forGetter(c -> c.goods),
                Codec.INT.optionalFieldOf("guards", 0).forGetter(c -> c.guards),
                Codec.BOOL.optionalFieldOf("materialized", false).forGetter(c -> c.materialized),
                UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(c -> c.members)
        ).apply(i, Caravan::new));

        public BlockPos goal() { return homeward ? from : to; }
    }

    private static final double PACE = 2.5;

    // ------------------------------------------------------------------ roads

    public static boolean isRoad(BlockState s) {
        return s.is(Blocks.DIRT_PATH) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.DEEPSLATE_TILES)
                || s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ANDESITE);
    }

    public static boolean onRoad(Entity e) {
        return isRoad(e.level().getBlockState(e.getBlockPosBelowThatAffectsMyMovement()));
    }

    // ------------------------------------------------------------------ routes

    private static boolean hasMarket(KingdomData d, Kingdom k) {
        for (Building b : d.progress(k).buildings) if (b.structure.equals("market_stall")) return true;
        return false;
    }

    private static boolean onRoute(KingdomData d, Kingdom k, BlockPos to) {
        for (Caravan c : d.caravans) if (c.kingdom.equals(k.id) && c.to.equals(to)) return true;
        return false;
    }

    /** Twice a day, each trade route without a caravan on it gets one. */
    public static void dispatch(ServerLevel level) {
        KingdomData d = Realm.data(level);
        String dim = KingdomData.dim(level);
        for (Kingdom k : new ArrayList<>(d.all())) {
            if (k.hall.isEmpty() || !k.hallDim.equals(dim)) continue;
            List<BlockPos> stops = new ArrayList<>();
            List<UUID> partners = new ArrayList<>();
            for (Kingdom o : d.all()) {
                Diplomacy.Relation rel = Diplomacy.relation(d, k.id, o.id);
                if (o == k || o.hall.isEmpty() || !o.hallDim.equals(dim) || (rel != Diplomacy.Relation.TRADING && rel != Diplomacy.Relation.ALLIED)) continue;
                if (k.wild && o.wild) continue;
                if (o.hall.get().distSqr(k.hall.get()) > 1500 * 1500) continue;
                stops.add(o.hall.get());
                partners.add(o.id);
            }
            if (!k.wild) {
                for (long p : d.progress(k).outposts) {
                    stops.add(BlockPos.of(p));
                    partners.add(k.id);
                }
            }
            if (stops.isEmpty() || !k.wild && !hasMarket(d, k)) continue;
            for (int i = 0; i < stops.size(); i++) {
                BlockPos to = stops.get(i);
                if (onRoute(d, k, to)) continue;
                int dist = (int) Math.sqrt(to.distSqr(k.hall.get()));
                Kingdom partner = d.byId(partners.get(i));
                int goods = 8 + dist / 50 + 2 * (partner == null ? 0 : d.progress(partner).maxTier());
                int guards = k.wild ? 2 : d.progress(k).caravanGuards;
                if (!k.wild && guards > 0) {
                    int wage = guards * 2;
                    if (!Diplomacy.pay(level, k, null, wage)) guards = 0;
                }
                if (!k.wild) goods += carrySurplus(level, k);
                BlockPos home = k.hall.get();
                d.caravans.add(new Caravan(UUID.randomUUID(), k.id, partners.get(i), dim, home.getX() + 0.5, home.getZ() + 0.5, home, to, false,
                        goods, guards, false, List.of()));
                d.markChanged();
                if (!k.wild) Citizen.tellKingdom(level, k, Component.literal("A caravan sets out for " + (partner == null || partner == k ? "your town at "
                        + to.getX() + " " + to.getZ() : partner.name) + (guards > 0 ? " with " + guards + " hired guards" : ", unguarded") + ".")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Loads the caravan with whatever the storehouse has far too much of; it sells better where it's scarce. Returns its worth in coins. */
    private static int carrySurplus(ServerLevel level, Kingdom k) {
        var store = Storehouse.containers(level, k);
        int worth = 0;
        for (ItemStack probe : new ItemStack[]{new ItemStack(Items.WHEAT), new ItemStack(Items.BREAD), new ItemStack(Items.OAK_LOG),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.LEATHER), new ItemStack(Items.COAL), new ItemStack(Items.CARROT)}) {
            int per = MarketBlock.perCoin(probe);
            if (per <= 0) continue;
            int have = Storehouse.count(store, s -> s.is(probe.getItem()));
            if (have < per * 24) continue;
            int load = Math.min(64, have - per * 16);
            Storehouse.take(store, s -> s.is(probe.getItem()), load);
            worth += (int) Math.ceil(load / (double) per * 1.5);
        }
        return worth;
    }

    // ------------------------------------------------------------------ the road

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        long t = level.getGameTime();
        if (t % 12000 == 4000) dispatch(level);
        if (t % 20 != 9) return;
        KingdomData d = Realm.data(level);
        for (Caravan c : new ArrayList<>(d.caravans)) {
            try {
                step(level, d, c);
            } catch (RuntimeException e) {
                DominionMod.LOGGER.error("Caravan {} lost its way", c.id, e);
                d.caravans.remove(c);
            }
        }
    }

    private static void step(ServerLevel level, KingdomData d, Caravan c) {
        Kingdom k = d.byId(c.kingdom);
        if (k == null || !c.dim.equals(KingdomData.dim(level))) {
            if (k == null) {
                dematerialize(level, c);
                d.caravans.remove(c);
            }
            return;
        }
        if (c.materialized) {
            Citizen merchant = c.members.isEmpty() || !(level.getEntity(c.members.get(0)) instanceof Citizen m) ? null : m;
            if (merchant == null || !merchant.isAlive()) {
                if (merchant == null && !c.members.isEmpty() && level.getEntity(c.members.get(0)) == null && seenFrom(level, c.x, c.z)) return;
                robbed(level, d, c, k);
                return;
            }
            c.x = merchant.getX();
            c.z = merchant.getZ();
            if (merchant.order() != com.dominionmod.dominion.entity.Order.MARCH) merchant.march(c.goal());
            if (!seenFrom(level, c.x, c.z)) dematerialize(level, c);
            else maybeAmbush(level, c, merchant);
        } else {
            BlockPos goal = c.goal();
            double dx = goal.getX() + 0.5 - c.x, dz = goal.getZ() + 0.5 - c.z, dist = Math.sqrt(dx * dx + dz * dz);
            BlockPos here = BlockPos.containing(c.x, 0, c.z);
            boolean road = level.hasChunk(here.getX() >> 4, here.getZ() >> 4)
                    && isRoad(level.getBlockState(here.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, here.getX(), here.getZ()) - 1)));
            double pace = road ? PACE * 1.6 : PACE;
            if (dist > 1) {
                double s = Math.min(pace, dist);
                c.x += dx / dist * s;
                c.z += dz / dist * s;
            }
            // unseen bandits: a small risk every second spent off the road on unclaimed land
            if (d.ownerAt(level, here) == null && !road) {
                double risk = 0.0008 * Math.pow(0.5, c.guards);
                if (level.getRandom().nextDouble() < risk) {
                    if (c.guards > 0 && level.getRandom().nextBoolean()) {
                        c.guards--;
                        if (!k.wild) Citizen.tellKingdom(level, k, Component.literal("Bandits fell on your caravan, but its guards drove them off"
                                + " (one guard was lost).").withStyle(ChatFormatting.YELLOW));
                    } else {
                        robbed(level, d, c, k);
                        return;
                    }
                }
            }
            if (seenFrom(level, c.x, c.z)) materialize(level, c, k);
        }
        if (Math.hypot(c.x - c.goal().getX(), c.z - c.goal().getZ()) < 10) arrive(level, d, c, k);
        d.markChanged();
    }

    private static boolean seenFrom(ServerLevel level, double x, double z) {
        if (!level.hasChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4)) return false;
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z) < 96 * 96) return true;
        return false;
    }

    private static void arrive(ServerLevel level, KingdomData d, Caravan c, Kingdom k) {
        if (!c.homeward) {
            c.homeward = true;
            Kingdom partner = d.byId(c.partner);
            if (partner != null && partner != k) {
                int toll = 3 + (int) Math.sqrt(c.from.distSqr(c.to)) / 100;
                if (!partner.wild) {
                    Diplomacy.receive(level, partner, toll);
                    Citizen.tellKingdom(level, partner, Component.literal("A caravan from " + k.name + " trades at your town and pays " + toll
                            + " Gold Coins in tolls.").withStyle(ChatFormatting.GOLD));
                }
            }
            for (UUID id : c.members) if (level.getEntity(id) instanceof Citizen m) m.march(c.from);
            return;
        }
        dematerialize(level, c);
        d.caravans.remove(c);
        if (!k.wild) {
            Diplomacy.receive(level, k, c.goods);
            Kingdom partner = d.byId(c.partner);
            Citizen.tellKingdom(level, k, Component.literal("Your caravan is back from " + (partner == null || partner == k ? "your outpost" : partner.name)
                    + " with " + c.goods + " Gold Coins.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static void robbed(ServerLevel level, KingdomData d, Caravan c, Kingdom k) {
        dematerialize(level, c);
        d.caravans.remove(c);
        d.markChanged();
        if (!k.wild) Citizen.tellKingdom(level, k, Component.literal("Bandits robbed your caravan near " + (int) c.x + " " + (int) c.z
                + ". Build roads, and hire guards at your Market, to keep them safe.").withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ in the flesh

    private static void materialize(ServerLevel level, Caravan c, Kingdom k) {
        RandomSource r = level.getRandom();
        int x = (int) Math.floor(c.x), z = (int) Math.floor(c.z);
        BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        Citizen merchant = ModContent.CITIZEN.get().create(level, EntitySpawnReason.EVENT);
        if (merchant == null) return;
        merchant.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        merchant.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
        merchant.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        level.addFreshEntity(merchant);
        merchant.joinTemporarily(k, "Merchant");
        merchant.march(c.goal());
        c.members.clear();
        c.members.add(merchant.getUUID());
        BreedHorse pony = ModContent.SHETLAND_PONY.get().create(level, EntitySpawnReason.EVENT);
        if (pony != null) {
            pony.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
            pony.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            pony.setTamed(true);
            level.addFreshEntity(pony);
            pony.setLeashedTo(merchant, true);
            SupplyCart cart = ModContent.SUPPLY_CART.get().create(level, EntitySpawnReason.EVENT);
            if (cart != null) {
                cart.snapTo(pony.getX(), pony.getY(), pony.getZ(), 0, 0);
                cart.hitch(pony, k.id);
                pony.setCartId(null); // a caravan pony doesn't go off on supply runs
                level.addFreshEntity(cart);
                c.members.add(cart.getUUID());
            }
            c.members.add(pony.getUUID());
        }
        for (int i = 0; i < c.guards; i++) {
            Citizen g = ModContent.CITIZEN.get().create(level, EntitySpawnReason.EVENT);
            if (g == null) continue;
            g.snapTo(at.getX() + 0.5 + r.nextInt(5) - 2, at.getY(), at.getZ() + 0.5 + r.nextInt(5) - 2, 0, 0);
            ItemStack[] kit = Settlements.kit(1, i % 2 == 0 ? "militia" : "archer");
            g.setItemSlot(EquipmentSlot.MAINHAND, kit[0]);
            if (!kit[1].isEmpty()) g.setItemSlot(EquipmentSlot.OFFHAND, kit[1]);
            g.setItemSlot(EquipmentSlot.CHEST, kit[2]);
            level.addFreshEntity(g);
            g.joinTemporarily(k, "Caravan Guard");
            g.march(c.goal());
            c.members.add(g.getUUID());
        }
        c.materialized = true;
    }

    private static void dematerialize(ServerLevel level, Caravan c) {
        for (UUID id : c.members) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        c.members.clear();
        c.materialized = false;
    }

    /** Off the road in the wilds, bandits may come for a caravan in plain sight. */
    private static void maybeAmbush(ServerLevel level, Caravan c, Citizen merchant) {
        if (level.getGameTime() % 200 != 9 || Realm.data(level).ownerAt(level, merchant.blockPosition()) != null || onRoad(merchant)) return;
        if (level.getRandom().nextFloat() > 0.04f * Math.pow(0.6, c.guards)) return;
        int n = 3 + level.getRandom().nextInt(3);
        for (int i = 0; i < n; i++) {
            Pillager b = net.minecraft.world.entity.EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
            if (b == null) continue;
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            int bx = (int) (merchant.getX() + Math.cos(a) * 14), bz = (int) (merchant.getZ() + Math.sin(a) * 14);
            b.snapTo(bx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz + 0.5, 0, 0);
            b.finalizeSpawn(level, level.getCurrentDifficultyAt(merchant.blockPosition()), EntitySpawnReason.EVENT, null);
            b.setCustomName(Component.literal("Bandit"));
            b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 2 == 0 ? Items.IRON_AXE : Items.CROSSBOW));
            b.setTarget(merchant);
            level.addFreshEntity(b);
        }
        Kingdom k = merchant.kingdom();
        if (k != null && !k.wild) Citizen.tellKingdom(level, k, Component.literal("Bandits! Your caravan is under attack near " + merchant.getBlockX()
                + " " + merchant.getBlockZ() + ".").withStyle(ChatFormatting.RED));
    }

    /** The caravan a merchant or guard belongs to. */
    public static @Nullable Caravan of(KingdomData d, UUID member) {
        for (Caravan c : d.caravans) if (c.members.contains(member)) return c;
        return null;
    }

    public static List<String> describe(ServerLevel level) {
        List<String> out = new ArrayList<>();
        KingdomData d = Realm.data(level);
        for (Caravan c : d.caravans) {
            Kingdom k = d.byId(c.kingdom), p = d.byId(c.partner);
            out.add((k == null ? "?" : k.name) + " <-> " + (p == null ? "?" : p.name) + String.format(" at %.0f,%.0f", c.x, c.z)
                    + (c.homeward ? " homeward" : " outbound") + ", " + c.goods + " coins of goods, " + c.guards + " guards"
                    + (c.materialized ? " (on the road)" : ""));
        }
        return out;
    }

    static boolean alive(LivingEntity e) {
        return e.isAlive();
    }
}
