package com.dominionmod.dominion.entity;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.build.Building;
import com.dominionmod.dominion.entity.wild.BreedHorse;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Soldiers on horseback. A soldier given a steed rides it into battle: melee soldiers become
 * knights (best on Clydesdales), archers become horse archers (best on Mustangs). Riders move at the
 * horse's pace and a knight at full gallop strikes with the weight of the horse behind the blow.
 * A Stables breeds kingdom horses and saddles them for soldiers who have none.
 */
public final class Cavalry {
    private Cavalry() {}

    private static final Identifier PACE = DominionMod.id("cavalry_pace");
    public static final String RIDER_TAG = "DominionRider";

    /** Moves a citizen (or the horse under them) toward a spot. */
    public static void moveTo(Citizen c, double x, double y, double z, double speed) {
        c.getNavigation().moveTo(x, y, z, speed);
    }

    /** The kingdom a horse belongs to: a breed horse raised for a kingdom, or a tamed horse whose owner is a member. */
    public static @Nullable UUID kingdomOf(AbstractHorse h) {
        if (h instanceof BreedHorse b && b.kingdomId() != null) return b.kingdomId();
        if (!h.isTamed() || h.getOwnerReference() == null || h.level().getServer() == null) return null;
        Kingdom k = KingdomData.get(h.level().getServer()).byPlayer(h.getOwnerReference().getUUID());
        return k == null ? null : k.id;
    }

    public static boolean riderTaken(AbstractHorse h) {
        return h.getPersistentData().contains(RIDER_TAG);
    }

    /** Gives a soldier their own horse and puts them in the saddle. */
    public static void assign(Citizen c, AbstractHorse h) {
        h.setTamed(true);
        if (!h.isSaddled()) h.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        if (h instanceof BreedHorse b && c.kingdomId() != null) b.setKingdomId(c.kingdomId());
        if (h.isLeashed()) h.removeLeash();
        h.getPersistentData().putString(RIDER_TAG, c.getUUID().toString());
        h.setPersistenceRequired();
        c.setSteed(h.getUUID());
        if (!c.isPassenger()) c.startRiding(h, true, true);
    }

    /** Releases a soldier's horse (it stays with the kingdom). */
    public static void release(Citizen c) {
        if (c.getVehicle() instanceof AbstractHorse h) {
            c.stopRiding();
            h.getPersistentData().remove(RIDER_TAG);
            pace(h, false);
        } else if (c.steed() != null && c.level() instanceof ServerLevel level && level.getEntity(c.steed()) instanceof AbstractHorse h) {
            h.getPersistentData().remove(RIDER_TAG);
        }
        c.setSteed(null);
    }

    /** A new horse of the given breed for a soldier, born saddled into their kingdom (armies on campaign). */
    public static @Nullable BreedHorse mountNew(ServerLevel level, Citizen c, BreedHorse.Breed breed) {
        var type = switch (breed) {
            case CLYDESDALE -> ModContent.CLYDESDALE.get();
            case MUSTANG -> ModContent.MUSTANG.get();
            case SHETLAND -> ModContent.SHETLAND_PONY.get();
        };
        BreedHorse h = type.create(level, EntitySpawnReason.EVENT);
        if (h == null) return null;
        h.snapTo(c.getX(), c.getY(), c.getZ(), c.getYRot(), 0);
        h.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(h);
        assign(c, h);
        return h;
    }

    private static void pace(AbstractHorse h, boolean on) {
        var inst = h.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        if (on && !inst.hasModifier(PACE)) inst.addTransientModifier(new AttributeModifier(PACE, 1.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (!on && inst.hasModifier(PACE)) inst.removeModifier(PACE);
    }

    /** Once a second for each citizen: keep riders in the saddle, and walk back to a horse they've left. */
    public static void tick(ServerLevel level, Citizen c) {
        if (c.getVehicle() instanceof AbstractHorse h) {
            pace(h, true);
            if (c.isSleeping() || c.isChild() || !c.job().soldier) {
                c.stopRiding();
                pace(h, false);
            }
            return;
        }
        UUID steed = c.steed();
        if (steed == null || !c.job().soldier || c.isSleeping() || !level.isBrightOutside() && c.order() == Order.DUTY) return;
        if (!(level.getEntity(steed) instanceof AbstractHorse h) || !h.isAlive()) {
            if (level.getEntity(steed) == null && c.tickCount % 600 != 0) return; // maybe just in an unloaded chunk
            c.setSteed(null);
            return;
        }
        if (h.isVehicle()) return;
        double d = c.distanceToSqr(h);
        if (d > 32 * 32) return;
        if (d < 2.5 * 2.5) c.startRiding(h, true, true);
        else if (c.getTarget() == null) c.getNavigation().moveTo(h, 0.6);
    }

    /** A knight's blow at the gallop: extra damage and a heavy shove. */
    public static float chargeBonus(Citizen c) {
        if (!(c.getVehicle() instanceof AbstractHorse h)) return 0;
        double speed = h.getDeltaMovement().horizontalDistance();
        if (speed < 0.12) return 1.0f;
        return h instanceof BreedHorse b && b.breed == BreedHorse.Breed.CLYDESDALE ? 6.0f : 4.0f;
    }

    // ------------------------------------------------------------------ the player hands over a horse

    /** A member leading a saddled horse on a lead right-clicks one of their soldiers: the soldier mounts it. */
    public static boolean giveLedHorse(Player p, Citizen c) {
        List<AbstractHorse> led = p.level().getEntitiesOfClass(AbstractHorse.class, p.getBoundingBox().inflate(12),
                h -> h.isLeashed() && h.getLeashHolder() == p);
        if (led.isEmpty()) return false;
        AbstractHorse h = led.get(0);
        if (!c.job().soldier) {
            p.sendOverlayMessage(Component.literal(c.baseName() + " isn't a soldier: only soldiers ride to war.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (!h.isTamed() || !h.isSaddled()) {
            p.sendOverlayMessage(Component.literal("Tame and saddle the horse first.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (c.isPassenger()) release(c);
        assign(c, h);
        p.sendSystemMessage(Component.literal(c.baseName() + " takes the reins. ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(c.job().melee() ? "A knight now: charge at the gallop for crushing blows." :
                        "A horse archer now: fast and hard to catch.").withStyle(ChatFormatting.GRAY)));
        return true;
    }

    // ------------------------------------------------------------------ stables

    /** Kingdom horses near a spot. */
    private static List<AbstractHorse> herd(ServerLevel level, Kingdom k, BlockPos at, double r) {
        return level.getEntitiesOfClass(AbstractHorse.class, new AABB(at).inflate(r), h -> h.isAlive() && k.id.equals(kingdomOf(h)));
    }

    /** A Stables was finished: it comes with a pair of horses. */
    public static void stablesBuilt(ServerLevel level, Kingdom k, BlockPos origin) {
        if (!level.isLoaded(origin)) return;
        if (!herd(level, k, origin, 10).isEmpty()) return;
        spawnFor(level, k, BreedHorse.Breed.CLYDESDALE, origin, false);
        spawnFor(level, k, BreedHorse.Breed.MUSTANG, origin, false);
        Citizen.tellKingdom(level, k, Component.literal("The Stables are open, with a Clydesdale and a Mustang in the stalls. Soldiers without a horse will"
                + " come and take one; the stable will breed more if the storehouse has wheat or hay to spare.").withStyle(ChatFormatting.GREEN));
    }

    private static @Nullable BreedHorse spawnFor(ServerLevel level, Kingdom k, BreedHorse.Breed breed, BlockPos near, boolean foal) {
        var type = switch (breed) {
            case CLYDESDALE -> ModContent.CLYDESDALE.get();
            case MUSTANG -> ModContent.MUSTANG.get();
            case SHETLAND -> ModContent.SHETLAND_PONY.get();
        };
        BreedHorse h = type.create(level, EntitySpawnReason.BREEDING);
        if (h == null) return null;
        h.snapTo(near.getX() + 0.5 + level.getRandom().nextInt(3) - 1, near.getY() + 1, near.getZ() + 0.5 + level.getRandom().nextInt(3) - 1,
                level.getRandom().nextFloat() * 360, 0);
        h.finalizeSpawn(level, level.getCurrentDifficultyAt(near), EntitySpawnReason.BREEDING, null);
        h.setTamed(true);
        h.setKingdomId(k.id);
        h.setPersistenceRequired();
        if (foal) h.setAge(-24000);
        else h.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        h.setHomeTo(near, 12);
        level.addFreshEntity(h);
        return h;
    }

    /** Every so often: each Stables breeds a foal if there's feed, saddles grown horses and mounts soldiers who have none. */
    public static void stablesTick(ServerLevel level) {
        if (level.getGameTime() % 600 != 211) return;
        KingdomData d = Realm.data(level);
        for (Kingdom k : d.all()) {
            for (Building b : d.progress(k).buildings) {
                if (!b.structure.equals("stables") || !b.dim.equals(KingdomData.dim(level)) || !level.isLoaded(b.origin)) continue;
                try {
                    runStables(level, k, b);
                } catch (RuntimeException e) {
                    DominionMod.LOGGER.error("Stables trouble", e);
                }
            }
        }
    }

    private static void runStables(ServerLevel level, Kingdom k, Building b) {
        List<AbstractHorse> herd = herd(level, k, b.origin, 20);
        // soldiers nearby without a horse take one
        List<Citizen> riders = level.getEntitiesOfClass(Citizen.class, new AABB(b.origin).inflate(64),
                c -> c.sameKingdom(k.id) && c.job().soldier && c.steed() == null && !c.isPassenger() && !c.isChild());
        for (Citizen c : riders) {
            BreedHorse.Breed want = c.job().melee() ? BreedHorse.Breed.CLYDESDALE : BreedHorse.Breed.MUSTANG;
            AbstractHorse pick = null;
            for (AbstractHorse h : herd) {
                if (h.isBaby() || h.isVehicle() || riderTaken(h) || h instanceof BreedHorse bh && bh.breed == BreedHorse.Breed.SHETLAND) continue;
                if (pick == null || h instanceof BreedHorse bh && bh.breed == want) pick = h;
            }
            if (pick == null) break;
            if (!pick.isSaddled() && !takeSaddle(level, k)) {
                Citizen.tellKingdom(level, k, Component.literal("The Stables need saddles: put a Saddle (or 3 Leather and an Iron Ingot) in the storehouse.")
                        .withStyle(ChatFormatting.YELLOW));
                break;
            }
            herd.remove(pick);
            pick.getPersistentData().putString(RIDER_TAG, c.getUUID().toString());
            if (!pick.isSaddled()) pick.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            c.setSteed(pick.getUUID());
            Citizen.tellKingdom(level, k, Component.literal(c.baseName() + " the " + c.job().title + " takes a horse from the Stables.")
                    .withStyle(ChatFormatting.GRAY));
        }
        // breed: up to four horses at a stable, fed from the storehouse
        int free = 0;
        for (AbstractHorse h : herd(level, k, b.origin, 20)) if (!riderTaken(h)) free++;
        if (free >= 4) return;
        var store = Storehouse.containers(level, k);
        boolean fed = Storehouse.take(store, s -> s.is(Items.HAY_BLOCK), 1).getCount() == 1
                || Storehouse.count(store, s -> s.is(Items.WHEAT)) >= 9 && Storehouse.take(store, s -> s.is(Items.WHEAT), 9).getCount() == 9;
        if (!fed) return;
        int knights = 0, archers = 0;
        for (int j : k.citizens.values()) {
            Job job = Job.byId(j);
            if (job.soldier && job.melee()) knights++;
            else if (job.soldier) archers++;
        }
        BreedHorse.Breed breed = level.getRandom().nextInt(5) == 0 ? BreedHorse.Breed.SHETLAND
                : knights >= archers ? BreedHorse.Breed.CLYDESDALE : BreedHorse.Breed.MUSTANG;
        BreedHorse foal = spawnFor(level, k, breed, b.origin, true);
        if (foal != null) Citizen.tellKingdom(level, k, Component.literal("A " + breed.name().charAt(0) + breed.name().substring(1).toLowerCase()
                + " foal was born at the Stables.").withStyle(ChatFormatting.GREEN));
    }

    private static boolean takeSaddle(ServerLevel level, Kingdom k) {
        var store = Storehouse.containers(level, k);
        if (Storehouse.take(store, s -> s.is(Items.SADDLE), 1).getCount() == 1) return true;
        if (Storehouse.count(store, s -> s.is(Items.LEATHER)) >= 3 && Storehouse.count(store, s -> s.is(Items.IRON_INGOT)) >= 1) {
            Storehouse.take(store, s -> s.is(Items.LEATHER), 3);
            Storehouse.take(store, s -> s.is(Items.IRON_INGOT), 1);
            return true;
        }
        return false;
    }

    /** For the status line. */
    public static String describe(Citizen c) {
        if (c.getVehicle() instanceof BreedHorse b) return ", riding a " + b.breed.name().charAt(0) + b.breed.name().substring(1).toLowerCase();
        if (c.getVehicle() instanceof AbstractHorse) return ", mounted";
        return c.steed() != null ? ", has a horse" : "";
    }

    static boolean isLiving(LivingEntity e) {
        return e.isAlive();
    }
}
