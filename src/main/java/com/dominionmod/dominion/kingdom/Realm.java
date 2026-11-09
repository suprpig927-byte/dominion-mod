package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.registry.ModContent;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The rules of running a kingdom: founding, claiming land, Influence, housing and population. */
public final class Realm {
    private Realm() {}

    public static final int START_INFLUENCE = 30;
    public static final int START_COINS = 20;
    public static final int RECRUIT_COST = 5;
    /** Influence earned per Minecraft day: a base amount plus some for every citizen. */
    public static final double DAILY_BASE = 5, DAILY_PER_CITIZEN = 3;
    public static final int HOUSING_RADIUS = 96;

    public static KingdomData data(ServerLevel level) {
        return KingdomData.get(level.getServer());
    }

    public static @Nullable Kingdom of(ServerPlayer p) {
        return KingdomData.get(p.level().getServer()).byPlayer(p.getUUID());
    }

    public static int claimCost(KingdomData d, Kingdom k) {
        return 10 + 3 * Math.max(0, d.claimCount(k) - 9);
    }

    public static double dailyInfluence(Kingdom k) {
        return DAILY_BASE + DAILY_PER_CITIZEN * k.citizens.size();
    }

    /** With the Taverns and Chapels. */
    public static double dailyInfluence(KingdomData d, Kingdom k) {
        return dailyInfluence(k) + Industry.dailyInfluence(d, k);
    }

    public static MutableComponent title(Kingdom k) {
        return Component.literal(k.name).withStyle(s -> s.withColor(k.textColor()).withBold(true));
    }

    // ------------------------------------------------------------------ founding

    /** Founds a kingdom around the Town Hall at {@code hall}. Returns an error message, or null on success. */
    public static @Nullable String found(ServerPlayer p, BlockPos hall, String rawName, int primary, int secondary, String flagDesign) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = data(level);
        String name = rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) return "Give your kingdom a name.";
        if (name.length() > 24) return "That name is too long (24 letters at most).";
        if (d.byPlayer(p.getUUID()) != null) return "You already belong to a kingdom.";
        if (d.byName(name) != null) return "A kingdom called " + name + " already exists.";
        if (!level.getBlockState(hall).is(ModContent.TOWN_HALL.get())) return "The Town Hall is gone.";
        if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hall)) > 64) return "Stand next to your Town Hall.";
        Kingdom owner = d.ownerAt(level, hall);
        if (owner != null) return "This land already belongs to " + owner.name + ".";

        primary = Math.floorMod(primary, 16);
        secondary = Math.floorMod(secondary, 16);
        ItemStack flag = Flags.toBanner(flagDesign, level.registryAccess());
        Kingdom k = new Kingdom(name, p.getUUID(), primary, secondary, flag);
        k.hall = java.util.Optional.of(hall.immutable());
        k.hallDim = KingdomData.dim(level);
        k.influence = START_INFLUENCE;
        d.add(k);
        // A new kingdom starts with the Town Hall's chunk and the eight around it.
        ChunkPos hc = ChunkPos.containing(hall);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos c = new ChunkPos(hc.x() + dx, hc.z() + dz);
                if (d.ownerAt(level, c) == null) d.claim(level, c, k);
            }
        raiseFlag(level, hall, k, p);

        give(p, new ItemStack(ModContent.GOLD_COIN.get(), START_COINS));
        give(p, new ItemStack(ModContent.COMMAND_BATON.get()));
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("A new kingdom rises: ").withStyle(ChatFormatting.GOLD)
                .append(title(k)).append(Component.literal(", ruled by " + p.getName().getString()).withStyle(ChatFormatting.GOLD)), false);
        p.sendSystemMessage(Component.literal("Recruit villagers in your land by right-clicking them with "
                + RECRUIT_COST + " Gold Coins. Each citizen needs a bed. Press K for your Realm screen.").withStyle(ChatFormatting.YELLOW));
        return null;
    }

    public static ItemStack plainBanner(int dye) {
        return new ItemStack(net.minecraft.world.item.Items.BANNER.pick(DyeColor.byId(dye)));
    }

    /** Plants the kingdom's flag on top of the Town Hall. */
    public static void raiseFlag(ServerLevel level, BlockPos hall, Kingdom k, @Nullable Player facing) {
        BlockPos top = hall.above();
        if (!level.getBlockState(top).isAir()) return;
        ItemStack flag = k.flag.isEmpty() ? plainBanner(k.primary) : k.flag;
        if (!(flag.getItem() instanceof BannerItem banner)) return;
        Block block = banner.getBlock();
        BlockState state = block.defaultBlockState();
        if (facing != null && state.hasProperty(BannerBlock.ROTATION)) {
            int rot = net.minecraft.util.Mth.floor((180.0F + facing.getYRot()) * 16.0F / 360.0F + 0.5) & 15;
            state = state.setValue(BannerBlock.ROTATION, rot);
        }
        level.setBlock(top, state, Block.UPDATE_ALL);
        var be = level.getBlockEntity(top);
        if (be != null) {
            be.applyComponentsFromItemStack(flag);
            be.setChanged();
            level.sendBlockUpdated(top, state, state, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ land

    /** Claims the chunk the player stands in. Returns a message to show. */
    public static Component claimHere(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = data(level);
        Kingdom k = d.byPlayer(p.getUUID());
        if (k == null) return err("You don't belong to a kingdom. Place a Town Hall to found one.");
        ChunkPos chunk = ChunkPos.containing(p.blockPosition());
        Kingdom owner = d.ownerAt(level, chunk);
        if (owner == k) return err("This chunk is already yours.");
        if (owner != null) return err("This land belongs to " + owner.name + ". Taking land by force comes with war (coming soon).");
        if (!d.touches(level, chunk, k) && !Watchtowers.covers(level, d, k, chunk))
            return err("New land has to border land you already own (or lie within sight of one of your watchtowers).");
        int cost = claimCost(d, k);
        if (k.influence < cost) return err(String.format("You need %d Influence to claim this (you have %.0f).", cost, k.influence));
        k.influence -= cost;
        d.claim(level, chunk, k);
        return Component.literal("Claimed this chunk for ").withStyle(ChatFormatting.GREEN).append(title(k))
                .append(Component.literal(String.format(" (-%d Influence, %.0f left)", cost, k.influence)).withStyle(ChatFormatting.GRAY));
    }

    public static Component unclaimHere(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = data(level);
        Kingdom k = d.byPlayer(p.getUUID());
        if (k == null) return err("You don't belong to a kingdom.");
        ChunkPos chunk = ChunkPos.containing(p.blockPosition());
        if (d.ownerAt(level, chunk) != k) return err("This chunk isn't yours.");
        if (k.hall.isPresent() && k.hallDim.equals(KingdomData.dim(level)) && ChunkPos.containing(k.hall.get()).equals(chunk))
            return err("You can't give up the chunk your Town Hall stands in.");
        d.unclaim(level, chunk);
        return Component.literal("You released this chunk. Its Influence is not refunded.").withStyle(ChatFormatting.YELLOW);
    }

    /** Can this player build, break and use things here? */
    public static boolean mayUse(Level level, BlockPos pos, Player p) {
        if (level.isClientSide() || p.isCreative() && p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return true;
        Kingdom owner = KingdomData.get(level.getServer()).ownerAt(level, pos);
        return owner == null || owner.isMember(p.getUUID());
    }

    // ------------------------------------------------------------------ population

    /** Beds inside the kingdom's land, near the Town Hall. Each one houses one citizen. */
    public static int beds(ServerLevel level, Kingdom k) {
        if (k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(level))) return 0;
        KingdomData d = data(level);
        return (int) level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), k.hall.get(), HOUSING_RADIUS, PoiManager.Occupancy.ANY)
                .filter(r -> d.ownerAt(level, r.getPos()) == k).count();
    }

    public static void addCitizen(Kingdom k, UUID citizen, Job job, KingdomData d) {
        k.citizens.put(citizen, job.ordinal());
        d.markChanged();
    }

    public static void removeCitizen(ServerLevel level, @Nullable UUID kingdom, UUID citizen) {
        KingdomData d = data(level);
        Kingdom k = d.byId(kingdom);
        if (k == null) return;
        k.citizens.remove(citizen);
        k.names.remove(citizen);
        k.beds.values().removeIf(citizen::equals);
        d.markChanged();
    }

    // ------------------------------------------------------------------ beds

    public static boolean isBedHead(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                && s.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
    }

    /** The head half of whichever bed is at {@code pos}. */
    public static @Nullable BlockPos bedHead(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return null;
        if (s.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) return pos.immutable();
        return pos.relative(s.getValue(net.minecraft.world.level.block.BedBlock.FACING)).immutable();
    }

    /** Gives a citizen a bed if they don't have a working one: the nearest unclaimed bed in the kingdom. */
    public static void ensureBed(ServerLevel level, Kingdom k, Citizen c) {
        BlockPos mine = k.bedOf(c.getUUID());
        if (mine != null) {
            if (!level.isLoaded(mine) || isBedHead(level, mine) && data(level).ownerAt(level, mine) == k) return;
            k.beds.remove(mine.asLong()); // the bed is gone
            data(level).markChanged();
        }
        if (k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(level))) return;
        KingdomData d = data(level);
        // Forget beds whose sleeper has left.
        k.beds.values().removeIf(id -> !k.citizens.containsKey(id));
        level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), k.hall.get(), HOUSING_RADIUS, PoiManager.Occupancy.ANY)
                .map(r -> r.getPos())
                .filter(pos -> !k.beds.containsKey(pos.asLong()) && d.ownerAt(level, pos) == k && isBedHead(level, pos))
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(c.blockPosition())))
                .ifPresent(pos -> {
                    k.beds.put(pos.asLong(), c.getUUID());
                    d.markChanged();
                });
    }

    /** Moves a citizen into a bed. Whoever slept there swaps into the citizen's old bed. */
    public static Component assignBed(ServerLevel level, Kingdom k, Citizen c, BlockPos clicked) {
        BlockPos head = bedHead(level, clicked);
        if (head == null) return err("That isn't a bed.");
        if (data(level).ownerAt(level, head) != k) return err("That bed isn't on your land.");
        BlockPos old = k.bedOf(c.getUUID());
        if (head.equals(old)) return Component.literal(c.baseName() + " already sleeps here.").withStyle(ChatFormatting.GRAY);
        UUID other = k.beds.get(head.asLong());
        if (old != null) k.beds.remove(old.asLong());
        k.beds.put(head.asLong(), c.getUUID());
        String msg = c.baseName() + " will sleep in this bed now.";
        if (other != null && !other.equals(c.getUUID())) {
            String otherName = k.names.getOrDefault(other, "Its old sleeper");
            if (old != null) {
                k.beds.put(old.asLong(), other);
                msg += " " + otherName + " moves to " + c.baseName() + "'s old bed.";
            } else {
                msg += " " + otherName + " will look for another bed.";
            }
        }
        if (c.isSleeping()) c.stopSleeping();
        data(level).markChanged();
        return Component.literal(msg).withStyle(ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------ flag

    /** The ruler changes the flag: it's re-raised over the Town Hall and every citizen's tabard updates. */
    public static Component setFlag(ServerPlayer p, String design) {
        ServerLevel level = (ServerLevel) p.level();
        Kingdom k = of(p);
        if (k == null || !k.owner.equals(p.getUUID())) return err("Only the ruler can change the flag.");
        k.flag = Flags.toBanner(design, level.registryAccess());
        data(level).markChanged();
        if (k.hall.isPresent() && k.hallDim.equals(KingdomData.dim(level))) {
            BlockPos top = k.hall.get().above();
            if (level.getBlockState(top).getBlock() instanceof BannerBlock || level.getBlockState(top).getBlock() instanceof net.minecraft.world.level.block.WallBannerBlock)
                level.removeBlock(top, false);
            raiseFlag(level, k.hall.get(), k, p);
        }
        return Component.literal("Your new flag flies over " + k.name + ".").withStyle(ChatFormatting.GREEN);
    }

    /** Called about once a minute: Influence trickles in over the day. */
    public static void minuteTick(ServerLevel overworld) {
        KingdomData d = data(overworld);
        for (Kingdom k : d.all()) k.influence += dailyInfluence(d, k) / 20.0;
        d.markChanged();
    }

    /**
     * Now and then, if there's a free bed and food to spare, two citizens near the Town Hall have a child,
     * who joins the kingdom and grows up in a day.
     */
    public static void birthTick(ServerLevel level) {
        KingdomData d = data(level);
        for (Kingdom k : d.all()) {
            if (k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(level)) || !level.isLoaded(k.hall.get())) continue;
            if (level.getRandom().nextFloat() > 0.25f) continue;
            int beds = beds(level, k);
            if (k.citizens.size() >= beds) continue;
            var store = Storehouse.containers(level, k);
            int food = Storehouse.count(store, s -> s.has(net.minecraft.core.component.DataComponents.FOOD));
            if (food < k.citizens.size() * 2 + 4) continue;
            java.util.List<Citizen> adults = level.getEntitiesOfClass(Citizen.class, new net.minecraft.world.phys.AABB(k.hall.get()).inflate(64),
                    c -> c.sameKingdom(k.id) && !c.isChild() && c.isAlive());
            if (adults.size() < 2) continue;
            Citizen a = adults.get(level.getRandom().nextInt(adults.size()));
            Citizen mate = null;
            for (Citizen b : adults) if (b != a && b.distanceToSqr(a) < 16 * 16) mate = b;
            if (mate == null) continue;
            for (int i = 0; i < 3; i++) Storehouse.take(store, s -> s.has(net.minecraft.core.component.DataComponents.FOOD), 1);
            Citizen baby = com.dominionmod.dominion.registry.ModContent.CITIZEN.get().create(level, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
            if (baby == null) continue;
            baby.snapTo((a.getX() + mate.getX()) / 2, a.getY(), (a.getZ() + mate.getZ()) / 2, a.getYRot(), 0);
            baby.setVillagerTypeForDebug(level.getRandom().nextBoolean() ? a.getVillagerType() : mate.getVillagerType());
            baby.makeChild();
            baby.joinKingdom(k, level);
            level.addFreshEntity(baby);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, baby.getX(), baby.getY() + 1, baby.getZ(), 8, 0.5, 0.5, 0.5, 0);
            Citizen.tellKingdom(level, k, Component.literal("A child, " + baby.baseName() + ", was born to " + a.baseName() + " and "
                    + mate.baseName() + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Find a loaded citizen by UUID anywhere. */
    public static @Nullable Citizen findCitizen(ServerLevel any, UUID id) {
        for (ServerLevel level : any.getServer().getAllLevels()) {
            if (level.getEntity(id) instanceof Citizen c) return c;
        }
        return null;
    }

    public static void give(Player p, ItemStack stack) {
        if (!p.getInventory().add(stack) && p.level() instanceof ServerLevel sl) p.spawnAtLocation(sl, stack);
    }

    public static Component err(String msg) {
        return Component.literal(msg).withStyle(ChatFormatting.RED);
    }
}
