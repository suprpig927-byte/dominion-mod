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
        /** A big, strong draft horse: lots of health, slow, a poor jumper. Dark coats with white socks. */
        CLYDESDALE(1.25, 34, 42, 0.16, 0.20, 0.45, 0.60,
                new Variant[]{Variant.BROWN, Variant.DARK_BROWN, Variant.BLACK, Variant.CHESTNUT},
                new Markings[]{Markings.WHITE, Markings.WHITE, Markings.WHITE_FIELD}),
        /** A wild horse of the plains: fast and a great jumper, but not tough. */
        MUSTANG(1.0, 18, 26, 0.28, 0.34, 0.70, 0.95,
                new Variant[]{Variant.CHESTNUT, Variant.BROWN, Variant.CREAMY, Variant.GRAY, Variant.BLACK},
                new Markings[]{Markings.NONE, Markings.WHITE_DOTS, Markings.BLACK_DOTS, Markings.WHITE_FIELD}),
        /** A tiny, sturdy pony. */
        SHETLAND(0.62, 12, 18, 0.20, 0.24, 0.40, 0.50, Variant.values(),
                new Markings[]{Markings.NONE, Markings.WHITE_FIELD, Markings.WHITE_DOTS, Markings.WHITE});

        public final double scale, minHp, maxHp, minSpeed, maxSpeed, minJump, maxJump;
        public final Variant[] coats;
        public final Markings[] markings;

        Breed(double scale, double minHp, double maxHp, double minSpeed, double maxSpeed, double minJump, double maxJump,
              Variant[] coats, Markings[] markings) {
            this.scale = scale;
            this.minHp = minHp;
            this.maxHp = maxHp;
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
            this.minJump = minJump;
            this.maxJump = maxJump;
            this.coats = coats;
            this.markings = markings;
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
        setLook(Util.getRandom(breed.coats, r), Util.getRandom(breed.markings, r));
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
        foal.setLook(this.random.nextBoolean() ? this.getVariant() : other.getVariant(),
                this.random.nextInt(3) == 0 ? Util.getRandom(breed.markings, this.random) : this.getMarkings());
        this.setOffspringAttributes(partner, foal);
        return foal;
    }
}
