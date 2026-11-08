package com.dominionmod.dominion.entity;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A lead ball fired from a Flintlock or Musket: fast, nearly flat, hits hard. */
public class Bullet extends ThrowableItemProjectile {
    private float damage = 8.0f;

    public Bullet(EntityType<? extends Bullet> type, Level level) {
        super(type, level);
    }

    public Bullet(Level level, LivingEntity shooter, float damage) {
        super(ModContent.BULLET_ENTITY.get(), shooter, level, new ItemStack(ModContent.BULLET.get()));
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return ModContent.BULLET.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.006;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > 80) this.discard();
        if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity target = hit.getEntity();
        if (this.level() instanceof ServerLevel sl) {
            target.hurtServer(sl, this.damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), damage);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.1);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        damage = in.getFloatOr("Damage", 8.0f);
    }
}
