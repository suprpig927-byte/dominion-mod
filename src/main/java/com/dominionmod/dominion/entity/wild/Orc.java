package com.dominionmod.dominion.entity.wild;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Tribal orcs. They live in villages around a war totem and attack players, villagers, citizens
 * and centaurs on sight. The biggest of them is the chieftain.
 */
public class Orc extends Monster {
    private static final EntityDataAccessor<Boolean> CHIEF = SynchedEntityData.defineId(Orc.class, EntityDataSerializers.BOOLEAN);

    public Orc(EntityType<? extends Orc> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.SCALE, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CHIEF, false);
    }

    public boolean isChief() {
        return this.entityData.get(CHIEF);
    }

    /** Makes this orc the village chieftain: bigger, tougher, and carrying a battle axe. */
    public void makeChief() {
        this.entityData.set(CHIEF, true);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(70.0);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0);
        this.getAttribute(Attributes.SCALE).setBaseValue(1.35);
        this.getAttribute(Attributes.ARMOR).setBaseValue(6.0);
        this.setHealth(this.getMaxHealth());
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModContent.BATTLE_AXE.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.5f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Citizen.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Centaur.class, true));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        ItemStack[] arms = {new ItemStack(Items.STONE_AXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.STONE_SWORD),
                new ItemStack(Items.WOODEN_SWORD), new ItemStack(ModContent.FLAIL.get()), new ItemStack(Items.IRON_SWORD)};
        this.setItemSlot(EquipmentSlot.MAINHAND, arms[this.random.nextInt(arms.length)]);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.1f);
        if (this.random.nextInt(3) == 0) this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return out;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putBoolean("Chief", isChief());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(CHIEF, in.getBooleanOr("Chief", false));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75f;
    }

    /** Orcs don't hurt each other. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Orc) && super.canAttack(target);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        com.dominionmod.dominion.DominionMod.LOGGER.info("SELFTEST orc died ({}) at {} from {} by {}", isChief() ? "chief" : "orc",
                blockPosition().toShortString(), source.getMsgId(), source.getEntity() == null ? "-" : source.getEntity().getType().toShortString());
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.KILLED)
            com.dominionmod.dominion.DominionMod.LOGGER.info("SELFTEST orc removed {} at {}", reason, blockPosition().toShortString());
        super.remove(reason);
    }
}
