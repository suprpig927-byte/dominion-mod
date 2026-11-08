package com.dominionmod.dominion.item;

import com.dominionmod.dominion.entity.Bullet;
import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Flintlock and Musket. Hold use to load a Bullet (takes a moment), use again to fire.
 * A loaded gun keeps its shot until fired.
 */
public class FirearmItem extends Item {
    public final int loadTicks;
    public final float damage;
    public final float velocity;
    public final float inaccuracy;

    public FirearmItem(Properties properties, int loadTicks, float damage, float velocity, float inaccuracy) {
        super(properties);
        this.loadTicks = loadTicks;
        this.damage = damage;
        this.velocity = velocity;
        this.inaccuracy = inaccuracy;
    }

    public static boolean loaded(ItemStack gun) {
        ChargedProjectiles c = gun.get(DataComponents.CHARGED_PROJECTILES);
        return c != null && !c.isEmpty();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (loaded(gun)) {
            gun.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (level instanceof ServerLevel sl) {
                fire(sl, player, player.getXRot(), player.getYRot(), this);
                gun.hurtAndBreak(1, player, hand);
            }
            player.getCooldowns().addCooldown(gun, 10);
            return InteractionResult.CONSUME;
        }
        if (player.getAbilities().instabuild || !findBullet(player).isEmpty()) {
            player.startUsingItem(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.6f, 0.7f);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack gun, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !loaded(gun)) {
            ItemStack bullet = findBullet(player);
            if (player.getAbilities().instabuild || !bullet.isEmpty()) {
                if (!player.getAbilities().instabuild) bullet.shrink(1);
                gun.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ItemStackTemplate.fromNonEmptyStack(new ItemStack(ModContent.BULLET.get()))));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
        return gun;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return loaded(stack) ? 0 : loadTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.CROSSBOW;
    }

    private static ItemStack findBullet(Player player) {
        for (InteractionHand h : InteractionHand.values()) {
            if (player.getItemInHand(h).is(ModContent.BULLET.get())) return player.getItemInHand(h);
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModContent.BULLET.get())) return inv.getItem(i);
        }
        return ItemStack.EMPTY;
    }

    /** Fires one shot from the shooter's eyes, with a bang and a puff of smoke. Used by players and Musketeers. */
    public static void fire(ServerLevel level, LivingEntity shooter, float xRot, float yRot, FirearmItem gun) {
        Bullet bullet = new Bullet(level, shooter, gun.damage);
        bullet.shootFromRotation(shooter, xRot, yRot, 0.0f, gun.velocity, gun.inaccuracy);
        level.addFreshEntity(bullet);
        Vec3 look = Vec3.directionFromRotation(xRot, yRot);
        Vec3 muzzle = shooter.getEyePosition().add(look.scale(1.2)).add(0, -0.15, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.08, 0.08, 0.08, 0.02);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.7f + level.getRandom().nextFloat() * 0.2f);
    }
}
