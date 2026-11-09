package com.dominionmod.dominion.entity.wild;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * A horse of a particular breed. It looks like a horse of the breed's coats and markings, at the
 * breed's size, with the breed's strengths, and foals take after their parents.
 */
public class BreedHorse extends Horse {
    public enum Breed {
        /** A big, strong draft horse: lots of health, slow, a poor jumper. Bay, black or dark bay with a bald face and feathered white legs. */
        CLYDESDALE(1.25, 34, 42, 0.16, 0.20, 0.45, 0.60, new Variant[]{Variant.BROWN, Variant.BLACK, Variant.DARK_BROWN}),
        /** A wild horse of the plains: fast and a great jumper, but not tough. Buckskin, grullo, pinto, sorrel or bay. */
        MUSTANG(1.0, 18, 26, 0.28, 0.34, 0.70, 0.95, new Variant[]{Variant.CREAMY, Variant.GRAY, Variant.WHITE, Variant.CHESTNUT, Variant.BROWN}),
        /** A tiny, sturdy, shaggy pony. Black, chestnut with a flaxen mane, piebald, dapple grey or palomino. */
        SHETLAND(0.62, 12, 18, 0.20, 0.24, 0.40, 0.50, new Variant[]{Variant.BLACK, Variant.CHESTNUT, Variant.WHITE, Variant.GRAY, Variant.CREAMY});

        public final double scale, minHp, maxHp, minSpeed, maxSpeed, minJump, maxJump;
        /** Each coat is a painted texture of its own; the vanilla colour it is stored as only picks which one. */
        public final Variant[] coats;

        Breed(double scale, double minHp, double maxHp, double minSpeed, double maxSpeed, double minJump, double maxJump, Variant[] coats) {
            this.scale = scale;
            this.minHp = minHp;
            this.maxHp = maxHp;
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
            this.minJump = minJump;
            this.maxJump = maxJump;
            this.coats = coats;
        }

        /** The breed's coat for a stored colour (horses from older saves may carry a colour the breed doesn't have). */
        public Variant coatFor(Variant v) {
            for (Variant c : coats) if (c == v) return v;
            return coats[v.getId() % coats.length];
        }
    }

    public final Breed breed;

    public BreedHorse(EntityType<? extends Horse> type, Level level, Breed breed) {
        super(type, level);
        this.breed = breed;
    }

    public static AttributeSupplier.Builder createAttributes(Breed breed) {
        return AbstractHorse.createBaseHorseAttributes().add(Attributes.SCALE, breed.scale);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(breed.minHp + random.nextDouble() * (breed.maxHp - breed.minHp));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(breed.minSpeed + random.nextDouble() * (breed.maxSpeed - breed.minSpeed));
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(breed.minJump + random.nextDouble() * (breed.maxJump - breed.minJump));
        this.setHealth(this.getMaxHealth());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        RandomSource r = level.getRandom();
        setLook(Util.getRandom(breed.coats, r), Markings.NONE);
        return out;
    }

    private static @Nullable Method LOOK;

    /** Horse keeps its coat setter private; reach it once by reflection. */
    public void setLook(Variant coat, Markings markings) {
        try {
            if (LOOK == null) {
                LOOK = Horse.class.getDeclaredMethod("setVariantAndMarkings", Variant.class, Markings.class);
                LOOK.setAccessible(true);
            }
            LOOK.invoke(this, coat, markings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.setComponent(net.minecraft.core.component.DataComponents.HORSE_VARIANT, coat);
        }
    }

    @Override
    public boolean canMate(Animal other) {
        return other instanceof BreedHorse h && h.breed == breed && super.canMate(other);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        @SuppressWarnings("unchecked")
        EntityType<BreedHorse> type = (EntityType<BreedHorse>) this.getType();
        BreedHorse foal = type.create(level, EntitySpawnReason.BREEDING);
        if (foal == null) return null;
        Horse other = partner instanceof Horse h ? h : this;
        Variant coat = this.random.nextInt(8) == 0 ? Util.getRandom(breed.coats, this.random)
                : breed.coatFor(this.random.nextBoolean() ? this.getVariant() : other.getVariant());
        foal.setLook(coat, Markings.NONE);
        this.setOffspringAttributes(partner, foal);
        if (kingdomId != null) {
            foal.setKingdomId(kingdomId);
            foal.setTamed(true);
        }
        return foal;
    }

    // ------------------------------------------------------------------ in a kingdom's service

    /** The kingdom this horse was bred for (stables, armies), if any. */
    private java.util.@org.jspecify.annotations.Nullable UUID kingdomId;
    /** The supply cart this pony pulls, if any. */
    private java.util.@org.jspecify.annotations.Nullable UUID cartId;

    public java.util.@org.jspecify.annotations.Nullable UUID kingdomId() { return kingdomId; }
    public void setKingdomId(java.util.@org.jspecify.annotations.Nullable UUID id) { this.kingdomId = id; }
    public java.util.@org.jspecify.annotations.Nullable UUID cartId() { return cartId; }
    public void setCartId(java.util.@org.jspecify.annotations.Nullable UUID id) { this.cartId = id; }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new com.dominionmod.dominion.entity.goal.SupplyRunGoal(this));
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (kingdomId != null) out.putString("DominionKingdom", kingdomId.toString());
        if (cartId != null) out.putString("DominionCart", cartId.toString());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        kingdomId = in.getString("DominionKingdom").map(BreedHorse::uuid).orElse(null);
        cartId = in.getString("DominionCart").map(BreedHorse::uuid).orElse(null);
    }

    private static java.util.@org.jspecify.annotations.Nullable UUID uuid(String s) {
        try {
            return java.util.UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
