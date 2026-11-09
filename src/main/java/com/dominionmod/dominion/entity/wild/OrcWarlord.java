package com.dominionmod.dominion.entity.wild;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Orc Warlord, master of a stronghold. Huge and armoured, he slams the ground to throw back
 * everyone around him, leaps on those who keep their distance, and when wounded lets out a war cry
 * that maddens every orc nearby and calls more to his side. Beat him for a hoard of gold and his cleaver.
 */
public class OrcWarlord extends Orc {
    private final ServerBossEvent bar = new ServerBossEvent(Mth.createInsecureUUID(this.random),
            Component.literal("Orc Warlord").withStyle(ChatFormatting.DARK_RED), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int slam = 60, leap = 100;
    private boolean cried;

    public OrcWarlord(EntityType<? extends Orc> type, Level level) {
        super(type, level);
        this.xpReward = 120;
    }

    public static AttributeSupplier.Builder createWarlordAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.SCALE, 1.7);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModContent.BATTLE_AXE.get()));
        for (EquipmentSlot s : EquipmentSlot.values()) this.setDropChance(s, 0f);
        this.setPersistenceRequired();
        return out;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bar.setProgress(getHealth() / getMaxHealth());
        LivingEntity t = getTarget();
        if (slam > 0) slam--;
        if (leap > 0) leap--;
        if (t == null || !t.isAlive()) return;
        double d = distanceTo(t);
        if (slam <= 0 && d < 4.5 && onGround()) groundSlam(level);
        else if (leap <= 0 && d > 6 && d < 16 && onGround()) {
            Vec3 dir = t.position().subtract(position()).normalize();
            setDeltaMovement(dir.x * 1.3, 0.75, dir.z * 1.3);
            leap = 140;
            level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.8f);
        }
        if (!cried && getHealth() < getMaxHealth() / 2) warCry(level);
    }

    private void groundSlam(ServerLevel level) {
        slam = 160;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.6f);
        var ground = level.getBlockState(blockPosition().below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 60, 2.5, 0.1, 2.5, 0.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(5, 2, 5),
                e -> e != this && !(e instanceof Orc) && e.isAlive())) {
            e.hurtServer(level, damageSources().mobAttack(this), 10f);
            Vec3 away = e.position().subtract(position()).normalize();
            e.push(away.x * 1.2, 0.7, away.z * 1.2);
            e.needsSync = true;
        }
    }

    private void warCry(ServerLevel level) {
        cried = true;
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.5f);
        for (Orc o : level.getEntitiesOfClass(Orc.class, getBoundingBox().inflate(20), Orc::isAlive)) {
            o.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
            o.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
        }
        for (int i = 0; i < 3; i++) {
            Orc o = ModContent.ORC.get().create(level, EntitySpawnReason.REINFORCEMENT);
            if (o == null) continue;
            o.snapTo(getX() + random.nextInt(7) - 3, getY(), getZ() + random.nextInt(7) - 3, random.nextFloat() * 360, 0);
            o.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
            o.setPersistenceRequired();
            o.setTarget(getTarget());
            level.addFreshEntity(o);
        }
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48)
            p.sendSystemMessage(Component.literal("The Warlord bellows a war cry, and more orcs answer!").withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            spawnAtLocation(level, new ItemStack(ModContent.GOLD_COIN.get(), 40 + random.nextInt(21)));
            ItemStack cleaver = new ItemStack(ModContent.BATTLE_AXE.get());
            cleaver.set(DataComponents.CUSTOM_NAME, Component.literal("Warlord's Cleaver").withStyle(ChatFormatting.GOLD));
            cleaver.set(DataComponents.RARITY, Rarity.EPIC);
            spawnAtLocation(level, cleaver);
            spawnAtLocation(level, new ItemStack(Items.SKELETON_SKULL));
            com.dominionmod.dominion.kingdom.OrcWarbands.warlordSlain(level, blockPosition());
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("The Orc Warlord has fallen!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }
}
