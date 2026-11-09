package com.dominionmod.dominion.entity.wild;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The Illager Overlord: an evoker grown huge on stolen power, who rules the Illager citadels. Besides
 * his fangs and vexes he raises a ring of fangs round himself, calls vindicator bodyguards, and
 * blinks away from anyone who corners him. Slaying him breaks the citadel's will.
 */
public class IllagerOverlord extends Evoker {
    private final ServerBossEvent bar = new ServerBossEvent(Mth.createInsecureUUID(this.random),
            Component.literal("Illager Overlord").withStyle(ChatFormatting.DARK_PURPLE), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int ring = 80, guards = 200, blink = 60;

    public IllagerOverlord(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createOverlordAttributes() {
        return Evoker.createAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.SCALE, 1.4);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
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
        if (ring > 0) ring--;
        if (guards > 0) guards--;
        if (blink > 0) blink--;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        double d = distanceTo(t);
        if (ring <= 0 && d < 6) fangRing(level);
        if (guards <= 0) callGuards(level);
        if (blink <= 0 && d < 3 && getHealth() < getMaxHealth() * 0.75f) blinkAway(level, t);
    }

    /** Two rings of fangs burst up around him. */
    private void fangRing(ServerLevel level) {
        ring = 140;
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2f, 0.7f);
        for (int r = 0; r < 2; r++) {
            int n = r == 0 ? 8 : 14;
            double rad = r == 0 ? 2.2 : 4.0;
            for (int i = 0; i < n; i++) {
                float a = (float) (i * Math.PI * 2 / n);
                double x = getX() + Mth.cos(a) * rad, z = getZ() + Mth.sin(a) * rad;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                if (Math.abs(y - getY()) > 3) continue;
                level.addFreshEntity(new EvokerFangs(level, x, y, z, a, r * 6, this));
            }
        }
    }

    /** Two vindicators answer his call. */
    private void callGuards(ServerLevel level) {
        guards = 400;
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.6f);
        for (int i = 0; i < 2; i++) {
            Vindicator v = net.minecraft.world.entity.EntityTypes.VINDICATOR.create(level, EntitySpawnReason.REINFORCEMENT);
            if (v == null) continue;
            v.snapTo(getX() + random.nextInt(5) - 2, getY(), getZ() + random.nextInt(5) - 2, random.nextFloat() * 360, 0);
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
            v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            v.setTarget(getTarget());
            com.dominionmod.dominion.kingdom.IllagerEmpire.enlist(v);
            level.addFreshEntity(v);
        }
        level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.5, getZ(), 30, 1.5, 1, 1.5, 0.1);
    }

    /** Cornered, he vanishes in a puff of smoke and appears a little way off. */
    private void blinkAway(ServerLevel level, LivingEntity from) {
        blink = 160;
        for (int i = 0; i < 12; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 16, z = getZ() + (random.nextDouble() - 0.5) * 16;
            if (from.distanceToSqr(x, from.getY(), z) < 36) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - getY()) > 4) continue;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            teleportTo(x, y, z);
            level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.5f, 0.8f);
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1));
            return;
        }
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            spawnAtLocation(level, new ItemStack(ModContent.GOLD_COIN.get(), 50 + random.nextInt(31)));
            spawnAtLocation(level, new ItemStack(Items.EMERALD, 8 + random.nextInt(9)));
            spawnAtLocation(level, new ItemStack(Items.TOTEM_OF_UNDYING));
            ItemStack banner = new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.BLACK));
            banner.set(DataComponents.CUSTOM_NAME, Component.literal("Overlord's Banner").withStyle(ChatFormatting.DARK_PURPLE));
            banner.set(DataComponents.RARITY, Rarity.EPIC);
            spawnAtLocation(level, banner);
            com.dominionmod.dominion.kingdom.IllagerEmpire.overlordSlain(level, blockPosition());
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("The Illager Overlord has fallen!")
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
