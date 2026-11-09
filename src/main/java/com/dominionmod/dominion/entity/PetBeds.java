package com.dominionmod.dominion.entity;

import com.dominionmod.dominion.block.PetBedBlock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Pet beds: at night a tame pet (cat, dog, otter, parrot...) that hasn't been told to sit walks to the
 * nearest free pet bed within 16 blocks, curls up, and slowly heals. It gets up at dawn, or when its
 * owner wanders far from home.
 */
public final class PetBeds {
    private PetBeds() {}

    /** Bed (packed position) -> the pet sleeping in it. Not saved: pets find their beds again each night. */
    private static final Map<Long, UUID> SLEEPERS = new HashMap<>();

    public static @Nullable String sleeperName(Level level, BlockPos bed) {
        UUID id = SLEEPERS.get(bed.asLong());
        if (id == null || !(level instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(id);
        return e == null ? null : e.getName().getString();
    }

    /** Adds the bedtime goal to a tame-able animal (on load and on spawn). */
    public static void onJoin(TamableAnimal pet) {
        if (pet.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof Bedtime)) return;
        pet.goalSelector.addGoal(2, new Bedtime(pet));
    }

    static final class Bedtime extends Goal {
        private final TamableAnimal pet;
        private @Nullable BlockPos bed;
        private int scan, heal;

        Bedtime(TamableAnimal pet) {
            this.pet = pet;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        private boolean wanted() {
            return pet.isTame() && !pet.isOrderedToSit() && !pet.isLeashed() && !pet.isPassenger() && !pet.isInWater()
                    && pet.level().isDarkOutside() && pet.getTarget() == null;
        }

        private boolean ownerHome(BlockPos at) {
            LivingEntity owner = pet.getOwner();
            return owner == null || owner.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 48 * 48;
        }

        private boolean free(BlockPos p) {
            UUID who = SLEEPERS.get(p.asLong());
            if (who == null || who.equals(pet.getUUID())) return true;
            Entity e = ((ServerLevel) pet.level()).getEntity(who);
            return e == null || !e.isAlive() || e.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) > 4;
        }

        @Override
        public boolean canUse() {
            if (!(pet.level() instanceof ServerLevel) || pet.tickCount % 40 != 0 || !wanted()) return false;
            if (bed == null || --scan <= 0 || !(pet.level().getBlockState(bed).getBlock() instanceof PetBedBlock)) {
                scan = 5;
                bed = null;
                double best = 17 * 17;
                BlockPos at = pet.blockPosition();
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -4, -16), at.offset(16, 4, 16))) {
                    if (!(pet.level().getBlockState(p).getBlock() instanceof PetBedBlock)) continue;
                    double d = p.distSqr(at);
                    if (d < best && free(p)) {
                        best = d;
                        bed = p.immutable();
                    }
                }
            }
            return bed != null && free(bed) && ownerHome(bed);
        }

        @Override
        public boolean canContinueToUse() {
            return bed != null && wanted() && ownerHome(bed) && pet.level().getBlockState(bed).getBlock() instanceof PetBedBlock;
        }

        @Override
        public void start() {
            if (bed == null) return;
            SLEEPERS.put(bed.asLong(), pet.getUUID());
            pet.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
            heal = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (bed == null) return;
            double dx = pet.getX() - (bed.getX() + 0.5), dz = pet.getZ() - (bed.getZ() + 0.5);
            if (dx * dx + dz * dz > 1.2) {
                if (pet.getNavigation().isDone()) pet.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
                return;
            }
            pet.getNavigation().stop();
            if (!pet.isInSittingPose()) {
                pet.snapTo(bed.getX() + 0.5, bed.getY() + 0.25, bed.getZ() + 0.5, pet.getYRot(), 0);
                pet.setInSittingPose(true);
                if (pet instanceof Cat cat) cat.setLying(true);
            }
            if (++heal % 100 == 0 && pet.getHealth() < pet.getMaxHealth()) {
                pet.heal(1.0f);
                ((ServerLevel) pet.level()).sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + 0.6, pet.getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
        }

        @Override
        public void stop() {
            if (bed != null && pet.getUUID().equals(SLEEPERS.get(bed.asLong()))) SLEEPERS.remove(bed.asLong());
            if (!pet.isOrderedToSit()) pet.setInSittingPose(false);
            if (pet instanceof Cat cat) cat.setLying(false);
        }
    }
}
