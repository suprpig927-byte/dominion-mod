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
        return 8 + 4 * d.claimCount(k);
    }

    public static double dailyInfluence(Kingdom k) {
        return DAILY_BASE + DAILY_PER_CITIZEN * k.citizens.size();
    }

    public static MutableComponent title(Kingdom k) {
        return Component.literal(k.name).withStyle(s -> s.withColor(k.textColor()).withBold(true));
    }

    // ------------------------------------------------------------------ founding

    /** Founds a kingdom around the Town Hall at {@code hall}. Returns an error message, or null on success. */
    public static @Nullable String found(ServerPlayer p, BlockPos hall, String rawName, int primary, int secondary) {
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
        ItemStack flag = p.getOffhandItem().getItem() instanceof BannerItem ? p.getOffhandItem().copyWithCount(1) : plainBanner(primary);
        Kingdom k = new Kingdom(name, p.getUUID(), primary, secondary, flag);
        k.hall = java.util.Optional.of(hall.immutable());
        k.hallDim = KingdomData.dim(level);
        k.influence = START_INFLUENCE;
        d.add(k);
        d.claim(level, ChunkPos.containing(hall), k);
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
        if (!d.touches(level, chunk, k)) return err("New land has to border land you already own.");
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
        if (k != null && k.citizens.remove(citizen) != null) d.markChanged();
    }

    /** Called about once a minute: Influence trickles in over the day. */
    public static void minuteTick(ServerLevel overworld) {
        KingdomData d = data(overworld);
        for (Kingdom k : d.all()) k.influence += dailyInfluence(k) / 20.0;
        d.markChanged();
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
