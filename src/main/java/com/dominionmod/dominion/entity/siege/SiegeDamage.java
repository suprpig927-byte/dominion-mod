package com.dominionmod.dominion.entity.siege;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Walls wear down under siege: every block soaks up damage in proportion to its hardness (cracks
 * show as it weakens) until it gives way. Blocks lost from a kingdom's land are remembered as ruins,
 * and its builders and engineers put them back once the fighting stops.
 */
public final class SiegeDamage {
    private SiegeDamage() {}

    private static final Map<ServerLevel, Map<Long, Float>> WEAR = new WeakHashMap<>();

    public static float hitPoints(BlockState s) {
        return 10f + 25f * Math.max(0f, s.getBlock().defaultDestroyTime());
    }

    /** Damages one block. Returns true if it broke. */
    public static boolean hit(ServerLevel level, BlockPos pos, float amount) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getBlock().defaultDestroyTime() < 0 || s.is(ModContent.TOWN_HALL.get())
                || s.is(ModContent.ORC_TOTEM.get()) || hitPoints(s) > 400) return false;
        Map<Long, Float> wear = WEAR.computeIfAbsent(level, l -> new HashMap<>());
        float worn = wear.merge(pos.asLong(), amount, Float::sum);
        float hp = hitPoints(s);
        int crackId = (int) (pos.asLong() ^ (pos.asLong() >>> 32)) | 0x40000000;
        if (worn < hp) {
            level.destroyBlockProgress(crackId, pos, Math.min(9, (int) (worn / hp * 10)));
            return false;
        }
        wear.remove(pos.asLong());
        level.destroyBlockProgress(crackId, pos, -1);
        KingdomData d = Realm.data(level);
        Kingdom owner = d.ownerAt(level, pos);
        if (owner != null && !s.hasBlockEntity()) d.ruins.add(new KingdomData.Ruin(owner.id, KingdomData.dim(level), pos.immutable(), s));
        d.markChanged();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, s.getSoundType().getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.8f);
        return true;
    }

    /** A boulder or cannonball strikes: blocks in a sphere and anyone close by take damage. */
    public static void blast(ServerLevel level, net.minecraft.world.phys.Vec3 at, float radius, float blockDamage, float entityDamage, DamageSource source) {
        BlockPos c = BlockPos.containing(at);
        int r = (int) Math.ceil(radius);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            double dist = Math.sqrt(p.distToCenterSqr(at));
            if (dist > radius + 0.5) continue;
            hit(level, p.immutable(), (float) (blockDamage * (1.0 - dist / (radius + 1.0))));
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(radius + 1.5))) {
            double dist = e.position().distanceTo(at);
            if (dist > radius + 2) continue;
            e.hurtServer(level, source, (float) (entityDamage * (1.0 - dist / (radius + 3.0))));
        }
    }
}
