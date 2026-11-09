package com.dominionmod.dominion.entity.wild;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * River otters: playful, at home in water and on land. They live in families along rivers and
 * swamps. Feed one raw cod or salmon to tame it (one try in three); a tame otter follows you,
 * sits when told, swims like a fish, floats on its back in calm water, and now and then dives
 * and brings you back a fish. Breed them with fish.
 */
public class Otter extends TamableAnimal {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Otter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLOATING = SynchedEntityData.defineId(Otter.class, EntityDataSerializers.BOOLEAN);
    public static final int VARIANTS = 2;
    private int fishingTicks;

    public Otter(EntityType<? extends Otter> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0f);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 20);
        this.fishingTicks = 1200 + this.random.nextInt(2400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, 0);
        b.define(FLOATING, false);
    }

    public int variant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int v) {
        this.entityData.set(VARIANT, Math.floorMod(v, VARIANTS));
    }

    /** Lying on its back at the surface of calm water. */
    public boolean isFloating() {
        return this.entityData.get(FLOATING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6) {
            @Override
            public boolean canUse() {
                return !isTame() && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1, this::isFood, false));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2, 8.0f, 3.0f));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.15));
        this.goalSelector.addGoal(7, new RandomSwimmingGoal(this, 1.0, 40));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isTame()) {
            if (isFood(held) && getHealth() < getMaxHealth()) {
                feed(player, hand, held, 2.0f, 3.0f);
                return InteractionResult.SUCCESS;
            }
            InteractionResult r = super.mobInteract(player, hand);
            if (!r.consumesAction() && isOwnedBy(player)) {
                setOrderedToSit(!isOrderedToSit());
                this.jumping = false;
                this.navigation.stop();
                return InteractionResult.SUCCESS.withoutItem();
            }
            return r;
        }
        if (!level().isClientSide() && (held.is(Items.COD) || held.is(Items.SALMON)) && !isBaby()) {
            held.consume(1, player);
            if (this.random.nextInt(3) == 0 && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(this, player)) {
                tame(player);
                this.navigation.stop();
                setOrderedToSit(true);
                level().broadcastEntityEvent(this, (byte) 7);
            } else {
                level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------ water

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(this.getSpeed() * 1.4f, input);
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        // bob up to the surface unless swimming somewhere
        if (input.lengthSqr() < 0.01 && this.isUnderWater()) this.setDeltaMovement(this.getDeltaMovement().add(0, 0.02, 0));
    }

    @Override
    public int getMaxAirSupply() {
        return 4800;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) return;
        boolean calm = isInWater() && !isUnderWater() && getDeltaMovement().horizontalDistanceSqr() < 0.0004 && !isOrderedToSit();
        if (calm != isFloating()) this.entityData.set(FLOATING, calm);
        // a tame otter in the water dives now and then and brings back a fish
        if (isTame() && isInWater() && !isOrderedToSit() && --fishingTicks <= 0) {
            fishingTicks = 2400 + this.random.nextInt(3600);
            LivingEntity owner = getOwner();
            if (owner != null && owner.distanceToSqr(this) < 24 * 24) {
                ItemStack fish = new ItemStack(this.random.nextFloat() < 0.65f ? Items.COD : Items.SALMON);
                ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), fish);
                drop.setPickUpDelay(10);
                level.addFreshEntity(drop);
                level.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.6, getZ(), 3, 0.3, 0.2, 0.3, 0);
                level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 12, 0.3, 0.1, 0.3, 0.1);
                playSound(SoundEvents.FISH_SWIM, 1.0f, 1.2f);
            }
        }
    }

    // ------------------------------------------------------------------ breeding, variants, saving

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Otter pup = ModContent.OTTER.get().create(level, EntitySpawnReason.BREEDING);
        if (pup != null) {
            pup.setVariant(partner instanceof Otter o && this.random.nextBoolean() ? o.variant() : variant());
            if (isTame() && getOwnerReference() != null) {
                pup.setOwnerReference(getOwnerReference());
                pup.setTame(true, true);
            }
        }
        return pup;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        setVariant(this.random.nextFloat() < 0.75f ? 0 : 1);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Variant", variant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setVariant(in.getIntOr("Variant", 0));
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isFloating() || this.random.nextInt(3) != 0 ? null : SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.55f;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.WOLF_STEP.value(), 0.08f, 1.6f);
    }
}
