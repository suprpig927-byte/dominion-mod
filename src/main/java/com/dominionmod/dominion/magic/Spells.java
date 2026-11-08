package com.dominionmod.dominion.magic;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Realm;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Casting: shared by players with a Spell Book and by Wizard citizens. */
public final class Spells {
    private Spells() {}

    private static final Map<UUID, long[]> READY_AT = new HashMap<>();

    /** Ticks until this caster can cast that spell again (0 = ready). */
    public static long cooldownLeft(LivingEntity caster, Spell s) {
        long[] r = READY_AT.get(caster.getUUID());
        return r == null ? 0 : Math.max(0, r[s.ordinal()] - caster.level().getGameTime());
    }

    private static void startCooldown(LivingEntity caster, Spell s) {
        READY_AT.computeIfAbsent(caster.getUUID(), u -> new long[Spell.values().length])[s.ordinal()] = caster.level().getGameTime() + s.cooldown;
    }

    /** Is {@code e} on the caster's side (themselves, their kingdom's citizens and members)? */
    public static boolean ally(LivingEntity caster, Entity e) {
        if (e == caster) return true;
        UUID kid = null;
        if (caster instanceof Citizen c && c.kingdom() != null) kid = c.kingdom().id;
        if (caster instanceof ServerPlayer p) {
            Kingdom k = Realm.of(p);
            if (k != null) kid = k.id;
        }
        if (kid == null) return false;
        if (e instanceof Citizen c) return c.sameKingdom(kid);
        if (e instanceof Player p && caster.level() instanceof ServerLevel sl) {
            Kingdom k = Realm.data(sl).byId(kid);
            return k != null && k.isMember(p.getUUID());
        }
        return false;
    }

    /**
     * Casts a spell. {@code aim} is the direction to cast in; {@code target} is an entity to aim at, if any.
     * Returns false if nothing happened (no room to blink, and so on).
     */
    public static boolean cast(ServerLevel level, LivingEntity caster, Spell spell, Vec3 aim, @Nullable LivingEntity target) {
        Vec3 eye = caster.getEyePosition();
        Vec3 dir = aim.normalize();
        switch (spell) {
            case FIREBALL -> {
                SmallFireball ball = new SmallFireball(level, caster, dir.scale(1.5));
                ball.setPos(eye.x + dir.x, eye.y + dir.y - 0.1, eye.z + dir.z);
                level.addFreshEntity(ball);
                level.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            case FANGS -> {
                float yaw = (float) Mth.atan2(dir.z, dir.x);
                for (int i = 0; i < 12; i++) {
                    double h = 1.25 * (i + 1);
                    double x = caster.getX() + Mth.cos(yaw) * h, z = caster.getZ() + Mth.sin(yaw) * h;
                    BlockPos ground = BlockPos.containing(x, caster.getY() + 2, z);
                    for (int k = 0; k < 6 && level.getBlockState(ground.below()).canBeReplaced(); k++) ground = ground.below();
                    level.addFreshEntity(new EvokerFangs(level, x, ground.getY(), z, yaw, i, caster));
                }
                level.playSound(null, caster.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.2f);
            }
            case FROST_NOVA -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(6.0), e -> !ally(caster, e) && e.isAlive())) {
                    e.hurtServer(level, level.damageSources().indirectMagic(caster, caster), 5.0f);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 160));
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, caster.getX(), caster.getY() + 1, caster.getZ(), 120, 3.0, 0.8, 3.0, 0.05);
                level.playSound(null, caster.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, caster.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.5f, 0.5f);
            }
            case LIGHTNING -> {
                Vec3 at = target != null ? target.position() : pick(level, caster, eye, dir, 40);
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt == null) return false;
                bolt.snapTo(at.x, at.y, at.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5, 3, 2.5),
                        e -> !ally(caster, e) && e.isAlive())) {
                    e.hurtServer(level, level.damageSources().indirectMagic(caster, caster), 10.0f);
                    e.igniteForSeconds(4);
                }
                level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0f, 1.1f);
            }
            case HEAL -> {
                caster.heal(6.0f);
                caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(8.0), e -> e != caster && ally(caster, e))) {
                    e.heal(4.0f);
                    e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
                    level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 2, e.getZ(), 3, 0.3, 0.3, 0.3, 0);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, caster.getX(), caster.getY() + 1, caster.getZ(), 30, 2.0, 1.0, 2.0, 0);
                level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.2f);
            }
            case BLINK -> {
                Vec3 to = pick(level, caster, eye, dir, 12);
                Vec3 land = to.subtract(dir.scale(0.8));
                BlockPos feet = BlockPos.containing(land.x, land.y - 1.0, land.z);
                for (int i = 0; i < 3 && !level.getBlockState(feet).isAir(); i++) feet = feet.above();
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) return false;
                level.sendParticles(ParticleTypes.PORTAL, caster.getX(), caster.getY() + 1, caster.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
                caster.teleportTo(land.x, feet.getY(), land.z);
                caster.resetFallDistance();
                level.sendParticles(ParticleTypes.PORTAL, land.x, feet.getY() + 1, land.z, 40, 0.4, 0.8, 0.4, 0.4);
                level.playSound(null, feet, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(spell.color, 1.4f), eye.x + dir.x * 0.6, eye.y - 0.2, eye.z + dir.z * 0.6,
                12, 0.25, 0.25, 0.25, 0.02);
        startCooldown(caster, spell);
        return true;
    }

    /** The first block (or the far end of the reach) along the caster's aim. */
    private static Vec3 pick(ServerLevel level, LivingEntity caster, Vec3 eye, Vec3 dir, double reach) {
        Vec3 end = eye.add(dir.scale(reach));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** A player casting from their book: checks cooldown and experience, then casts. */
    public static void playerCast(ServerPlayer p, Spell spell) {
        long left = cooldownLeft(p, spell);
        if (left > 0) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(spell.title + " is recharging (" + (left / 20 + 1) + "s)")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        if (!p.getAbilities().instabuild && p.totalExperience < spell.cost) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Not enough experience to cast " + spell.title
                    + " (needs " + spell.cost + " XP)").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        LivingEntity target = lookTarget(level, p, 40);
        if (cast(level, p, spell, p.getLookAngle(), target) && !p.getAbilities().instabuild) p.giveExperiencePoints(-spell.cost);
    }

    /** The living thing under the player's crosshair, if any. */
    private static @Nullable LivingEntity lookTarget(ServerLevel level, Player p, double reach) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(reach));
        Vec3 blockHit = pick(level, p, eye, p.getLookAngle(), reach);
        double best = eye.distanceToSqr(blockHit);
        LivingEntity found = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> e != p && e.isAlive())) {
            var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < best) {
                best = eye.distanceToSqr(hit.get());
                found = e;
            }
        }
        return found;
    }
}
