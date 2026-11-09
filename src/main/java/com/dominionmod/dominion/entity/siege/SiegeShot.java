package com.dominionmod.dominion.entity.siege;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A trebuchet's boulder (a long, high arc that crushes masonry) or a cannonball (fast and flat, bursting on impact). */
public class SiegeShot extends ThrowableItemProjectile {
    public static final double GRAVITY = 0.05;

    public SiegeShot(EntityType<? extends SiegeShot> type, Level level) {
        super(type, level);
    }

    public boolean cannonball() {
        return this.getType() == ModContent.CANNONBALL_ENTITY.get();
    }

    @Override
    protected Item getDefaultItem() {
        return this.getType() == ModContent.CANNONBALL_ENTITY.get() ? ModContent.CANNONBALL.get() : Items.COBBLESTONE;
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > 200) this.discard();
        if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) {
            sl.sendParticles(cannonball() ? ParticleTypes.SMOKE : ParticleTypes.CLOUD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 at = hit.getLocation();
        var source = this.damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null);
        if (cannonball()) {
            SiegeDamage.blast(sl, at, 1.6f, 70f, 14f, source);
            sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 1.1f);
        } else {
            SiegeDamage.blast(sl, at, 1.8f, 85f, 12f, source);
            sl.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.6, 0.6, 0.6, 0.05);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 2.0f, 0.6f);
        }
        this.discard();
    }

    /**
     * The launch velocity that lands a shot at {@code to}: for a high lob (trebuchet) or a flat shot
     * (cannon), found by simulating the flight with the projectile's own gravity and drag.
     */
    public static Vec3 aim(Vec3 from, Vec3 to, boolean lob) {
        double dx = to.x - from.x, dz = to.z - from.z, horiz = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.toRadians(lob ? 50 : 12);
        double best = 1.0, bestErr = Double.MAX_VALUE;
        for (double v = 0.6; v <= 4.0; v += 0.02) {
            double vx = Math.cos(angle) * v, vy = Math.sin(angle) * v, x = 0, y = 0;
            for (int t = 0; t < 400; t++) {
                x += vx;
                y += vy;
                vx *= 0.99;
                vy = vy * 0.99 - GRAVITY;
                if (vy < 0 && y <= to.y - from.y) break;
            }
            double err = Math.abs(x - horiz);
            if (err < bestErr) {
                bestErr = err;
                best = v;
            }
        }
        double h = Math.cos(angle) * best;
        return new Vec3(dx / Math.max(horiz, 0.001) * h, Math.sin(angle) * best, dz / Math.max(horiz, 0.001) * h);
    }
}
