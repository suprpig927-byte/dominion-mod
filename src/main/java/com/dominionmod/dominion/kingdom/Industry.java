package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.build.Palette;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Workshops: buildings that hire an idle citizen (anyone with empty hands and no class) and make
 * things for the storehouse while their workers are on the job in daylight. Every minute each worker
 * on site turns out one batch. Mine, Lumber Mill, Mill, Bakery, Butcher and Harbor make goods; the
 * Tavern and Chapel bring Influence, travellers and healing.
 */
public final class Industry {
    private Industry() {}

    /** A workshop: the trade it hires, and what it's for. */
    public record Shop(String id, Job trade, String makes) {}

    public static final Map<String, Shop> SHOPS = new LinkedHashMap<>();

    static {
        shop("mine", Job.MINER, "stone, coal and ore");
        shop("lumber_mill", Job.SAWYER, "planks and sticks from logs");
        shop("mill", Job.MILLER, "flour from wheat");
        shop("bakery", Job.BAKER, "bread from flour (or wheat)");
        shop("butcher", Job.BUTCHER, "meat, leather, wool and feathers from your herds");
        shop("harbor", Job.FISHER, "fish");
        shop("tavern", Job.INNKEEPER, "Influence and new citizens");
        shop("chapel", Job.PRIEST, "Influence and healing");
    }

    private static void shop(String id, Job trade, String makes) {
        SHOPS.put(id, new Shop(id, trade, makes));
    }

    public static @Nullable Shop shopOf(Building b) {
        return SHOPS.get(b.structure);
    }

    /** How many hands a workshop takes on, by tier. */
    public static int slots(Building b) {
        if (b.structure.equals("tavern") || b.structure.equals("chapel")) return 1;
        return b.tier >= Palette.ROYAL ? 3 : b.tier >= Palette.STONE ? 2 : 1;
    }

    // ------------------------------------------------------------------ the clock

    public static void tick(ServerLevel level) {
        long t = level.getGameTime();
        if (t % 100 != 37) return;
        KingdomData d = Realm.data(level);
        String dim = KingdomData.dim(level);
        boolean produce = t % 1200 == 637;
        boolean morning = t % 24000 == 1037;
        for (Kingdom k : new ArrayList<>(d.all())) {
            if (k.wild) continue;
            Progress pr = d.progress(k);
            if (morning) morning(level, d, k);
            for (Building b : pr.buildings) {
                Shop shop = shopOf(b);
                if (shop == null || !b.dim.equals(dim) || !level.isLoaded(b.origin)) continue;
                try {
                    staff(level, d, k, b, shop);
                    if (produce) work(level, d, k, b, shop);
                } catch (RuntimeException e) {
                    DominionMod.LOGGER.error("Workshop {} at {} misbehaved", b.structure, b.origin, e);
                }
            }
        }
    }

    // ------------------------------------------------------------------ hiring

    private static void staff(ServerLevel level, KingdomData d, Kingdom k, Building b, Shop shop) {
        // let go of anyone who's gone, been given another job, or if the shop is closed
        for (UUID id : new ArrayList<>(b.workers)) {
            if (!(level.getEntity(id) instanceof Citizen c)) {
                if (!k.citizens.containsKey(id)) b.workers.remove(id);
                continue;
            }
            boolean keep = b.open && c.isAlive() && c.sameKingdom(k.id) && c.job() == shop.trade();
            if (!keep) {
                b.workers.remove(id);
                if (c.specialization() == shop.trade()) c.hire(null);
                d.markChanged();
            }
        }
        if (!b.open || b.workers.size() >= slots(b)) return;
        BlockPos at = b.center();
        Citizen best = null;
        double bd = 96 * 96;
        for (Citizen c : level.getEntitiesOfClass(Citizen.class, new AABB(at).inflate(96, 32, 96), c -> c.isAlive() && c.sameKingdom(k.id))) {
            if (c.isChild() || c.temporary() || c.specialization() != null || c.job() != Job.UNASSIGNED || c.order() != Order.DUTY) continue;
            double dd = c.distanceToSqr(at.getX(), at.getY(), at.getZ());
            if (dd < bd) {
                bd = dd;
                best = c;
            }
        }
        if (best == null) {
            b.problem = b.workers.isEmpty() ? "No one to hire: recruit a citizen and leave their hands empty" : "";
            return;
        }
        best.hire(shop.trade());
        ItemStack tool = switch (shop.trade()) {
            case MINER -> new ItemStack(Items.IRON_PICKAXE);
            case FISHER -> new ItemStack(Items.FISHING_ROD);
            default -> ItemStack.EMPTY;
        };
        if (!tool.isEmpty()) best.setItemSlot(EquipmentSlot.MAINHAND, tool);
        b.workers.add(best.getUUID());
        b.problem = "";
        d.markChanged();
        Citizen.tellKingdom(level, k, Component.literal(best.baseName() + " takes work as the " + shop.trade().title + " at the " + b.name() + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    /** The workshop a trade citizen works at, if any. */
    public static @Nullable Building workplaceOf(ServerLevel level, Citizen c) {
        Kingdom k = c.kingdom();
        if (k == null) return null;
        for (Building b : Realm.data(level).progress(k).buildings) if (b.workers.contains(c.getUUID())) return b;
        return null;
    }

    // ------------------------------------------------------------------ work

    private static void work(ServerLevel level, KingdomData d, Kingdom k, Building b, Shop shop) {
        if (!level.isBrightOutside()) return;
        BlockPos at = b.center();
        int present = 0;
        for (UUID id : b.workers) {
            if (level.getEntity(id) instanceof Citizen c && c.isAlive() && c.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 14 * 14 && !c.isSleeping()) {
                present++;
                c.markWorked();
            }
        }
        if (present == 0) {
            if (!b.workers.isEmpty()) b.problem = "The " + shop.trade().title + " isn't at work";
            return;
        }
        var store = Storehouse.containers(level, k);
        RandomSource r = level.getRandom();
        List<ItemStack> made = new ArrayList<>();
        String problem = "";
        for (int w = 0; w < present; w++) {
            switch (b.structure) {
                case "mine" -> mine(b, r, made);
                case "lumber_mill" -> {
                    if (!saw(store, made)) problem = "Needs logs in the storehouse";
                }
                case "mill" -> {
                    ItemStack wheat = Storehouse.take(store, s -> s.is(Items.WHEAT), 3);
                    if (wheat.getCount() < 3) {
                        if (!wheat.isEmpty()) Storehouse.insert(store, wheat);
                        problem = "Needs wheat (3 a sack of flour)";
                    } else made.add(new ItemStack(ModContent.FLOUR.get(), b.tier >= Palette.STONE ? 3 : 2));
                }
                case "bakery" -> {
                    ItemStack flour = Storehouse.take(store, s -> s.is(ModContent.FLOUR.get()), 1);
                    if (!flour.isEmpty()) made.add(new ItemStack(Items.BREAD, 2 + (b.tier >= Palette.BRICK ? 1 : 0)));
                    else {
                        ItemStack wheat = Storehouse.take(store, s -> s.is(Items.WHEAT), 3);
                        if (wheat.getCount() == 3) made.add(new ItemStack(Items.BREAD));
                        else {
                            if (!wheat.isEmpty()) Storehouse.insert(store, wheat);
                            problem = "Needs flour or wheat";
                        }
                    }
                }
                case "butcher" -> {
                    if (!butcher(level, store, b, made)) problem = "Needs 3+ cows, pigs, sheep or chickens within 24 blocks";
                }
                case "harbor" -> fish(level, b, r, made);
                case "tavern" -> problem = "";
                case "chapel" -> bless(level, k, b);
                default -> {}
            }
        }
        for (ItemStack s : made) {
            ItemStack left = Storehouse.insert(store, s);
            if (!left.isEmpty()) {
                problem = "The storehouse is full";
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, left);
                level.addFreshEntity(drop);
            }
        }
        if (!made.isEmpty()) {
            Map<Item, Integer> sum = new LinkedHashMap<>();
            for (ItemStack s : made) sum.merge(s.getItem(), s.getCount(), Integer::sum);
            List<String> parts = new ArrayList<>();
            sum.forEach((item, n) -> parts.add(n + " " + new ItemStack(item).getHoverName().getString()));
            b.lastOutput = String.join(", ", parts);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 6, 1.5, 0.5, 1.5, 0);
        }
        b.problem = problem;
    }

    private static void mine(Building b, RandomSource r, List<ItemStack> out) {
        float luck = 1f + 0.25f * b.tier;
        out.add(new ItemStack(r.nextFloat() < 0.3f ? Items.COBBLED_DEEPSLATE : Items.COBBLESTONE, 6 + r.nextInt(5)));
        if (r.nextFloat() < 0.45f * luck) out.add(new ItemStack(Items.COAL, 1 + r.nextInt(3)));
        if (r.nextFloat() < 0.28f * luck) out.add(new ItemStack(Items.RAW_IRON, 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.2f * luck) out.add(new ItemStack(Items.RAW_COPPER, 2 + r.nextInt(3)));
        if (r.nextFloat() < 0.12f * luck) out.add(new ItemStack(Items.LAPIS_LAZULI, 2 + r.nextInt(4)));
        if (r.nextFloat() < 0.1f * luck) out.add(new ItemStack(Items.REDSTONE, 2 + r.nextInt(4)));
        if (r.nextFloat() < 0.06f * luck) out.add(new ItemStack(Items.RAW_GOLD));
        if (b.tier >= Palette.STONE && r.nextFloat() < 0.012f * luck) out.add(new ItemStack(Items.DIAMOND));
        if (r.nextFloat() < 0.006f * luck) out.add(new ItemStack(Items.EMERALD));
    }

    private static final Map<Item, Item> PLANKS = new HashMap<>();

    static {
        PLANKS.put(Items.OAK_LOG, Items.OAK_PLANKS);
        PLANKS.put(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
        PLANKS.put(Items.BIRCH_LOG, Items.BIRCH_PLANKS);
        PLANKS.put(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS);
        PLANKS.put(Items.ACACIA_LOG, Items.ACACIA_PLANKS);
        PLANKS.put(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
        PLANKS.put(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS);
        PLANKS.put(Items.CHERRY_LOG, Items.CHERRY_PLANKS);
        PLANKS.put(Items.PALE_OAK_LOG, Items.PALE_OAK_PLANKS);
    }

    private static boolean saw(List<Container> store, List<ItemStack> out) {
        ItemStack logs = Storehouse.take(store, s -> PLANKS.containsKey(s.getItem()), 2);
        if (logs.isEmpty()) return false;
        out.add(new ItemStack(PLANKS.get(logs.getItem()), 6 * logs.getCount()));
        out.add(new ItemStack(Items.STICK, 2 * logs.getCount()));
        return true;
    }

    private record Herd(EntityType<?> type, Item feed, Item meat, Item extra) {}

    private static final List<Herd> HERDS = List.of(
            new Herd(EntityTypes.COW, Items.WHEAT, Items.COOKED_BEEF, Items.LEATHER),
            new Herd(EntityTypes.PIG, Items.CARROT, Items.COOKED_PORKCHOP, Items.AIR),
            new Herd(EntityTypes.SHEEP, Items.WHEAT, Items.COOKED_MUTTON, Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)),
            new Herd(EntityTypes.CHICKEN, Items.WHEAT_SEEDS, Items.COOKED_CHICKEN, Items.FEATHER));

    /** Breeds the herds round the shop with grain from the storehouse, and culls the biggest herd down to its breeding stock. */
    private static boolean butcher(ServerLevel level, List<Container> store, Building b, List<ItemStack> out) {
        BlockPos at = b.center();
        List<Animal> near = level.getEntitiesOfClass(Animal.class, new AABB(at).inflate(24, 8, 24), a -> a.isAlive() && !a.isBaby());
        Herd biggest = null;
        List<Animal> biggestHerd = List.of();
        for (Herd h : HERDS) {
            List<Animal> herd = near.stream().filter(a -> a.getType() == h.type() && !a.hasCustomName()).toList();
            // breed a pair if there's feed
            List<Animal> ready = herd.stream().filter(a -> a.canFallInLove() && a.getAge() == 0).limit(2).toList();
            if (ready.size() == 2 && herd.size() < 12) {
                Item feed = h.type() == EntityTypes.PIG && Storehouse.count(store, s -> s.is(Items.CARROT)) < 2 ? Items.BEETROOT : h.feed();
                ItemStack got = Storehouse.take(store, s -> s.is(feed), 2);
                if (got.getCount() == 2) for (Animal a : ready) a.setInLove(null);
                else if (!got.isEmpty()) Storehouse.insert(store, got);
            }
            if (herd.size() >= 3 && herd.size() > biggestHerd.size()) {
                biggest = h;
                biggestHerd = herd;
            }
        }
        if (biggest == null) return false;
        Animal cull = biggestHerd.get(biggestHerd.size() - 1);
        level.sendParticles(ParticleTypes.POOF, cull.getX(), cull.getY() + 0.5, cull.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        cull.discard();
        out.add(new ItemStack(biggest.meat(), 1 + level.getRandom().nextInt(3)));
        if (biggest.extra() != Items.AIR) out.add(new ItemStack(biggest.extra(), 1 + level.getRandom().nextInt(2)));
        return true;
    }

    private static void fish(ServerLevel level, Building b, RandomSource r, List<ItemStack> out) {
        BlockPos at = b.center();
        int water = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 1, 12))) {
            if (level.getFluidState(p).isSource()) water++;
            if (water > 80) break;
        }
        boolean open = water > 60;
        boolean cooked = b.tier >= Palette.STONE;
        int n = 1 + (open ? 1 + r.nextInt(2) : 0);
        for (int i = 0; i < n; i++) {
            float f = r.nextFloat();
            Item fish = f < 0.6f ? (cooked ? Items.COOKED_COD : Items.COD) : f < 0.9f ? (cooked ? Items.COOKED_SALMON : Items.SALMON)
                    : f < 0.95f ? Items.TROPICAL_FISH : Items.PUFFERFISH;
            out.add(new ItemStack(fish));
        }
        if (open && r.nextFloat() < 0.04f) {
            Item[] treasure = {Items.NAUTILUS_SHELL, Items.NAME_TAG, Items.SADDLE, Items.BOW, Items.LILY_PAD, Items.INK_SAC};
            out.add(new ItemStack(treasure[r.nextInt(treasure.length)]));
        }
    }

    /** The Priest's blessing: the hurt and the poisoned within 32 blocks are tended. */
    private static void bless(ServerLevel level, Kingdom k, Building b) {
        BlockPos at = b.center();
        for (Citizen c : level.getEntitiesOfClass(Citizen.class, new AABB(at).inflate(32, 12, 32), c -> c.isAlive() && c.sameKingdom(k.id))) {
            if (c.getHealth() < c.getMaxHealth()) c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, true, true));
            c.removeEffect(MobEffects.POISON);
            c.removeEffect(MobEffects.WITHER);
            c.removeEffect(MobEffects.WEAKNESS);
        }
        b.lastOutput = "Tended the sick";
        level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ the kingdom-wide effects

    /** Influence a day from staffed Taverns (+3) and Chapels (+2). */
    public static double dailyInfluence(KingdomData d, Kingdom k) {
        double n = 0;
        for (Building b : d.progress(k).buildings) {
            if (b.workers.isEmpty() || !b.open) continue;
            if (b.structure.equals("tavern")) n += 3;
            if (b.structure.equals("chapel")) n += 2;
        }
        return n;
    }

    /** Each morning a staffed tavern may put up a traveller who stays and joins the kingdom. */
    public static void morning(ServerLevel level, KingdomData d, Kingdom k) {
        for (Building b : d.progress(k).buildings) {
            if (!b.structure.equals("tavern") || b.workers.isEmpty() || !b.open || !b.dim.equals(KingdomData.dim(level)) || !level.isLoaded(b.origin)) continue;
            if (level.getRandom().nextFloat() > (b.tier >= Palette.STONE ? 0.8f : 0.5f)) continue;
            if (k.citizens.size() >= Realm.beds(level, k)) {
                b.problem = "A traveller would have stayed, but there are no free beds";
                continue;
            }
            var store = Storehouse.containers(level, k);
            if (Storehouse.count(store, s -> s.has(DataComponents.FOOD)) < 10) {
                b.problem = "Travellers won't stay without 10 food in store";
                continue;
            }
            BlockPos at = b.center();
            Citizen c = ModContent.CITIZEN.get().create(level, EntitySpawnReason.EVENT);
            if (c == null) continue;
            String[] looks = {"plains", "desert", "taiga", "savanna", "snow", "jungle", "swamp"};
            c.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
            c.setVillagerTypeForDebug(looks[level.getRandom().nextInt(looks.length)]);
            c.joinKingdom(k, level);
            level.addFreshEntity(c);
            b.lastOutput = c.baseName() + " stayed on";
            Citizen.tellKingdom(level, k, Component.literal("A traveller, " + c.baseName() + ", liked the " + b.name()
                    + " so much they've decided to stay. They swear fealty to your kingdom.").withStyle(ChatFormatting.GREEN));
        }
    }

    /** Arrow damage bonus from an Archery Range (0.25, or 0.4 in Stone and up). */
    public static float archeryBonus(ServerLevel level, @Nullable Kingdom k) {
        if (k == null) return 0;
        float best = 0;
        for (Building b : Realm.data(level).progress(k).buildings)
            if (b.structure.equals("archery_range")) best = Math.max(best, b.tier >= Palette.STONE ? 0.4f : 0.25f);
        return best;
    }

    /** One line per workshop for the Industry tab: id|name|trade|workers|slots|open|last|problem|x z */
    public static List<String> rows(ServerLevel level, Kingdom k) {
        List<String> out = new ArrayList<>();
        KingdomData d = Realm.data(level);
        for (Building b : d.progress(k).buildings) {
            Shop shop = shopOf(b);
            if (shop == null) continue;
            List<String> names = new ArrayList<>();
            for (UUID id : b.workers) names.add(k.names.getOrDefault(id, "?"));
            out.add(b.id + "|" + b.name() + "|" + shop.trade().title + "|" + String.join(", ", names) + "|" + slots(b) + "|" + b.open + "|"
                    + b.lastOutput.replace('|', '/') + "|" + b.problem.replace('|', '/') + "|" + b.origin.getX() + " " + b.origin.getZ() + "|" + shop.makes());
        }
        for (Building b : d.progress(k).buildings) {
            if (!b.structure.equals("archery_range")) continue;
            out.add(b.id + "|" + b.name() + "|-||0|true|Archers hit " + Math.round(archeryBonus(level, k) * 100) + "% harder||"
                    + b.origin.getX() + " " + b.origin.getZ() + "|a sharper eye for every archer");
        }
        return out;
    }

    /** Opens or closes a workshop (from the Industry tab). */
    public static Component toggle(ServerLevel level, Kingdom k, String buildingId) {
        for (Building b : Realm.data(level).progress(k).buildings) {
            if (!b.id.toString().equals(buildingId) || shopOf(b) == null) continue;
            b.open = !b.open;
            if (!b.open) {
                for (UUID id : b.workers) if (level.getEntity(id) instanceof Citizen c) {
                    c.hire(null);
                    if (c.getMainHandItem().is(Items.IRON_PICKAXE) || c.getMainHandItem().is(Items.FISHING_ROD)) c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                b.workers.clear();
            }
            Realm.data(level).markChanged();
            return Component.literal(b.name() + (b.open ? " is open: it will hire again." : " is closed and its workers are let go."));
        }
        return Component.literal("No such workshop.");
    }
}
