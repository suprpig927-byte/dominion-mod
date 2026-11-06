package com.dominionmod.dominion.event;

import com.dominionmod.dominion.command.Orders;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

/** Land protection, recruiting, friendly fire, border notices and the kingdom clock. */
public final class Events {
    private Events() {}

    public static void register(IEventBus bus) {
        bus.addListener(Events::onLevelTick);
        bus.addListener(Events::onBreak);
        bus.addListener(Events::onRightClickBlock);
        bus.addListener(Events::onEntityInteract);
        bus.addListener(Events::onIncomingDamage);
        bus.addListener(Events::onDeath);
        bus.addListener(Events::onFinalizeSpawn);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            Orders.forget(e.getEntity().getUUID());
            LAST_OWNER.remove(e.getEntity().getUUID());
        });
    }

    // ------------------------------------------------------------------ clock

    private static final Map<UUID, UUID> LAST_OWNER = new HashMap<>();
    private static final UUID WILDS = new UUID(0, 0);

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        long t = level.getGameTime();
        if (level.dimension() == Level.OVERWORLD && t % 1200 == 0) Realm.minuteTick(level);
        if (level.dimension() == Level.OVERWORLD) com.dominionmod.dominion.debug.SelfTest.tick(level);
        if (level.dimension() == Level.OVERWORLD && t % 20 == 0) com.dominionmod.dominion.kingdom.Scholarship.tick(level);
        if (t % 2400 == 1200) Realm.birthTick(level);
        if (t % 10 != 0) return;
        KingdomData d = Realm.data(level);
        for (ServerPlayer p : level.players()) {
            borderNotice(p, d);
            if (p.getMainHandItem().is(ModContent.BLUEPRINT.get())) {
                Kingdom k = d.byPlayer(p.getUUID());
                int color = k == null ? 0xFFFFFF : DyeColor.byId(k.primary).getTextureDiffuseColor();
                com.dominionmod.dominion.build.Construction.preview(p, p.getMainHandItem(), color);
            }
            if (t % 20 == 0 && p.getMainHandItem().is(ModContent.COMMAND_BATON.get())) {
                Orders.showSelection(p);
                showBorders(level, p, d);
            }
        }
    }

    private static void borderNotice(ServerPlayer p, KingdomData d) {
        Kingdom here = d.ownerAt(p.level(), p.blockPosition());
        UUID now = here == null ? WILDS : here.id;
        UUID before = LAST_OWNER.put(p.getUUID(), now);
        if (before == null || before.equals(now)) return;
        if (here == null) {
            p.sendOverlayMessage(Component.literal("~ Wilderness ~").withStyle(ChatFormatting.DARK_GREEN));
        } else {
            p.sendOverlayMessage(Component.literal("Entering the Kingdom of ").withStyle(ChatFormatting.GRAY).append(Realm.title(here)));
        }
    }

    /** Draws claim borders (in each kingdom's color) around the player while they hold the baton. */
    private static void showBorders(ServerLevel level, ServerPlayer p, KingdomData d) {
        ChunkPos center = ChunkPos.containing(p.blockPosition());
        double y = p.getY() + 0.4;
        for (int cx = center.x() - 2; cx <= center.x() + 2; cx++) {
            for (int cz = center.z() - 2; cz <= center.z() + 2; cz++) {
                ChunkPos c = new ChunkPos(cx, cz);
                UUID owner = d.ownerId(level, c);
                if (owner == null) continue;
                Kingdom k = d.byId(owner);
                if (k == null) continue;
                DustParticleOptions dust = new DustParticleOptions(DyeColor.byId(k.primary).getTextureDiffuseColor(), 1.3f);
                int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
                // north, south, west, east edges
                if (!Objects.equals(owner, d.ownerId(level, new ChunkPos(cx, cz - 1)))) edge(level, p, dust, x0, z0, 1, 0, y);
                if (!Objects.equals(owner, d.ownerId(level, new ChunkPos(cx, cz + 1)))) edge(level, p, dust, x0, z0 + 16, 1, 0, y);
                if (!Objects.equals(owner, d.ownerId(level, new ChunkPos(cx - 1, cz)))) edge(level, p, dust, x0, z0, 0, 1, y);
                if (!Objects.equals(owner, d.ownerId(level, new ChunkPos(cx + 1, cz)))) edge(level, p, dust, x0 + 16, z0, 0, 1, y);
            }
        }
    }

    private static void edge(ServerLevel level, ServerPlayer p, DustParticleOptions dust, int x, int z, int dx, int dz, double y) {
        for (int i = 0; i <= 16; i += 2) {
            level.sendParticles(p, dust, true, false, x + dx * i, y, z + dz * i, 1, 0, 0.3, 0, 0);
        }
    }

    // ------------------------------------------------------------------ protection

    private static void onBreak(BreakBlockEvent e) {
        Player p = e.getPlayer();
        if (p == null || !(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!Realm.mayUse(level, e.getPos(), p)) {
            e.setCanceled(true);
            deny(p, level, e.getPos());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Level level = e.getLevel();
        if (level.isClientSide()) return;
        Player p = e.getEntity();
        BlockPos pos = e.getPos();
        // The baton on a bed assigns it, instead of trying to sleep in it.
        if (p instanceof ServerPlayer sp && p.getItemInHand(e.getHand()).is(ModContent.COMMAND_BATON.get())
                && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock && Realm.mayUse(level, pos, p)) {
            Orders.assignBed(sp, pos);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            return;
        }
        // Building signs: show the building and (sneaking) start an upgrade.
        if (p instanceof ServerPlayer sp2 && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.SignBlock) {
            KingdomData d = KingdomData.get(level.getServer());
            Kingdom owner = d.ownerAt(level, pos);
            if (owner != null) {
                var b = d.progress(owner).buildingWithSign(KingdomData.dim(level), pos.asLong());
                if (b != null) {
                    e.setCanceled(true);
                    e.setCancellationResult(InteractionResult.SUCCESS_SERVER);
                    if (owner.isMember(p.getUUID())) sp2.sendSystemMessage(com.dominionmod.dominion.build.Buildings.clicked(sp2, owner, b));
                    else sp2.sendOverlayMessage(Component.literal("The " + b.name() + " of ").append(Realm.title(owner)));
                    return;
                }
            }
        }
        // Opening a labeled storehouse chest shows what it's for.
        if (p instanceof ServerPlayer sp3 && !p.getItemInHand(e.getHand()).is(ModContent.STORAGE_LABEL.get())) {
            KingdomData d = KingdomData.get(level.getServer());
            Kingdom owner = d.ownerAt(level, pos);
            if (owner != null && owner.isMember(p.getUUID())) {
                String label = d.progress(owner).labels.get(pos.asLong());
                if (label != null) sp3.sendOverlayMessage(Component.literal(com.dominionmod.dominion.kingdom.StorageCategory.byName(label).title + " storage")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        BlockPos placeAt = e.getFace() == null ? pos : pos.relative(e.getFace());
        if (!Realm.mayUse(level, pos, p) || !Realm.mayUse(level, placeAt, p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            deny(p, level, pos);
        }
    }

    private static void deny(Player p, Level level, BlockPos pos) {
        Kingdom k = KingdomData.get(level.getServer()).ownerAt(level, pos);
        if (k == null) return;
        p.sendOverlayMessage(Component.literal("This land belongs to ").withStyle(ChatFormatting.RED).append(Realm.title(k)));
    }

    // ------------------------------------------------------------------ recruiting

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Villager v) || !(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = p.getMainHandItem();
        if (!held.is(ModContent.GOLD_COIN.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Kingdom k = d.byPlayer(p.getUUID());
        if (k == null) {
            p.sendSystemMessage(Realm.err("Found a kingdom first: place a Town Hall."));
            return;
        }
        if (v.isBaby()) {
            p.sendOverlayMessage(Realm.err("Children can't swear fealty."));
            return;
        }
        if (d.ownerAt(level, v.blockPosition()) != k) {
            p.sendSystemMessage(Realm.err("You can only recruit villagers standing on your own land. Lead them in with emeralds or claim their village."));
            return;
        }
        if (held.getCount() < Realm.RECRUIT_COST) {
            p.sendOverlayMessage(Realm.err("Recruiting costs " + Realm.RECRUIT_COST + " Gold Coins."));
            return;
        }
        int beds = Realm.beds(level, k);
        if (k.citizens.size() >= beds) {
            p.sendSystemMessage(Realm.err("Not enough housing: " + k.citizens.size() + " citizens, " + beds
                    + " beds. Place more beds on your land (within " + Realm.HOUSING_RADIUS + " blocks of your Town Hall)."));
            return;
        }
        Citizen c = ModContent.CITIZEN.get().create(level, EntitySpawnReason.CONVERSION);
        if (c == null) return;
        c.snapTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), v.getXRot());
        c.copyLookFrom(v);
        c.joinKingdom(k, level);
        level.addFreshEntity(c);
        v.discard();
        if (!p.isCreative()) held.shrink(Realm.RECRUIT_COST);
        level.playSound(null, c.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        p.sendSystemMessage(Component.literal(c.baseName() + " swears fealty to ").withStyle(ChatFormatting.GREEN).append(Realm.title(k))
                .append(Component.literal("! Hand them a hoe, axe, sword or bow to give them a job.").withStyle(ChatFormatting.GRAY)));
    }

    // ------------------------------------------------------------------ combat

    /** The kingdom an entity fights for: a citizen's kingdom, or a player's. */
    public static @Nullable UUID side(@Nullable Entity e) {
        if (e instanceof Projectile proj) e = proj.getOwner();
        if (e instanceof Citizen c) return c.kingdomId();
        if (e instanceof Player p && p.level().getServer() != null) {
            Kingdom k = KingdomData.get(p.level().getServer()).byPlayer(p.getUUID());
            return k == null ? null : k.id;
        }
        return null;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        Entity attacker = e.getSource().getEntity();
        if (attacker == null || attacker == victim) return;
        UUID a = side(attacker), v = side(victim);
        // No friendly fire inside a kingdom.
        if (a != null && a.equals(v)) {
            e.setCanceled(true);
            return;
        }
        // Until wars arrive, players can't hurt other kingdoms' citizens.
        if (victim instanceof Citizen && v != null && attacker instanceof Player) e.setCanceled(true);
    }

    /** When a commander falls, nearby troops waver. */
    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Kingdom k = Realm.of(p);
        if (k == null) return;
        int n = 0;
        for (Citizen c : p.level().getEntitiesOfClass(Citizen.class, p.getBoundingBox().inflate(24), c -> c.sameKingdom(k.id) && c.job().soldier)) {
            c.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
            c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            c.returnToDuty();
            n++;
        }
        if (n > 0) Citizen.tellKingdom((ServerLevel) p.level(), k, Component.literal(p.getName().getString()
                + " has fallen! " + n + " soldiers waver and fall back to their posts.").withStyle(ChatFormatting.RED));
    }

    /** Hostile mobs don't spawn naturally on peaceful kingdom land. */
    private static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy) || e.getSpawnType() != EntitySpawnReason.NATURAL) return;
        Level level = e.getLevel().getLevel();
        if (level.getServer() == null) return;
        Kingdom k = KingdomData.get(level.getServer()).ownerAt(level, BlockPos.containing(e.getX(), e.getY(), e.getZ()));
        if (k != null && k.peaceful) e.setSpawnCancelled(true);
    }
}
