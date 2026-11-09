package com.dominionmod.dominion.entity.siege;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Diplomacy;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A siege engine: a battering ram, a trebuchet or a cannon. An army's engines roll toward the
 * enemy town and go to work on the first wall in their way. Your own engines need a crew (a citizen
 * of your kingdom or you, close by) and ammunition (stone for a trebuchet, cannonballs for a
 * cannon), and fire on whatever you mark with the Command Baton in Siege mode.
 */
public class SiegeEngine extends PathfinderMob {
    public enum Kind {
        RAM("Battering Ram", 90, 0.16, 0, 3, 50), TREBUCHET("Trebuchet", 60, 0.12, 14, 56, 160), CANNON("Cannon", 50, 0.14, 5, 40, 100);

        public final String title;
        public final double health, speed;
        public final int minRange, maxRange, reload;

        Kind(String title, double health, double speed, int minRange, int maxRange, int reload) {
            this.title = title;
            this.health = health;
            this.speed = speed;
            this.minRange = minRange;
            this.maxRange = maxRange;
            this.reload = reload;
        }
    }

    /** Game time of the last swing or shot, for the client's animation. */
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(SiegeEngine.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(SiegeEngine.class, EntityDataSerializers.FLOAT);

    private @Nullable UUID kingdomId;
    private @Nullable BlockPos dest;
    private @Nullable BlockPos fireAt;
    /** Shots loaded; -1 = an army's engine, supplied by its train. */
    private int ammo;
    private int cooldown;

    public SiegeEngine(EntityType<? extends SiegeEngine> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public Kind kind() {
        EntityType<?> t = this.getType();
        return t == ModContent.TREBUCHET.get() ? Kind.TREBUCHET : t == ModContent.CANNON.get() ? Kind.CANNON : Kind.RAM;
    }

    public static AttributeSupplier.Builder createAttributes(Kind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.MOVEMENT_SPEED, kind.speed)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, -1000);
        builder.define(PITCH, 0f);
    }

    public int lastAction() { return this.entityData.get(ACTION); }
    public float barrelPitch() { return this.entityData.get(PITCH); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Work());
    }

    // ------------------------------------------------------------------ ownership

    public @Nullable UUID kingdomId() { return kingdomId; }

    public void setKingdom(@Nullable UUID k) { this.kingdomId = k; }

    /** An army's engine: march on {@code dest} and keep firing (the army brings its own shot). */
    public void campaign(BlockPos dest) {
        this.dest = dest.immutable();
        this.ammo = -1;
    }

    public void setFireAt(@Nullable BlockPos pos) {
        this.fireAt = pos == null ? null : pos.immutable();
    }

    public @Nullable BlockPos fireAt() { return fireAt; }

    public int ammo() { return ammo; }

    private @Nullable Kingdom kingdom() {
        return kingdomId == null || this.level().getServer() == null ? null : KingdomData.get(this.level().getServer()).byId(kingdomId);
    }

    /** Places an engine of an army on the march. */
    public static @Nullable SiegeEngine spawnFor(ServerLevel level, Kingdom k, String troop, BlockPos at, BlockPos dest) {
        EntityType<SiegeEngine> type = switch (troop) {
            case "trebuchet" -> ModContent.TREBUCHET.get();
            case "cannon" -> ModContent.CANNON.get();
            default -> ModContent.BATTERING_RAM.get();
        };
        SiegeEngine e = type.create(level, EntitySpawnReason.EVENT);
        if (e == null) return null;
        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        e.setKingdom(k.id);
        e.campaign(dest);
        level.addFreshEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ work

    private boolean crewed() {
        if (ammo < 0) return true;
        AABB box = getBoundingBox().inflate(5);
        for (Citizen c : this.level().getEntitiesOfClass(Citizen.class, box, c -> c.isAlive() && c.sameKingdom(kingdomId) && !c.isChild())) return true;
        Kingdom k = kingdom();
        for (Player p : this.level().players()) if (k != null && k.isMember(p.getUUID()) && p.distanceToSqr(this) < 6 * 6) return true;
        return false;
    }

    private boolean enemyLand(BlockPos p) {
        if (this.level().getServer() == null) return false;
        Kingdom owner = KingdomData.get(this.level().getServer()).ownerAt(this.level(), p);
        if (owner == null || owner.id.equals(kingdomId)) return false;
        return ammo < 0 || Diplomacy.hostile(this.level().getServer(), kingdomId, owner.id);
    }

    /** The first standing block of enemy wall between here and {@code goal}, within reach. */
    private @Nullable BlockPos obstacleToward(BlockPos goal, double reach) {
        Vec3 from = position(), to = Vec3.atCenterOf(goal);
        Vec3 dir = new Vec3(to.x - from.x, 0, to.z - from.z);
        double len = dir.length();
        if (len < 1) return null;
        dir = dir.scale(1 / len);
        for (double t = 1.5; t < Math.min(len, reach); t += 0.5) {
            int x = (int) Math.floor(from.x + dir.x * t), z = (int) Math.floor(from.z + dir.z * t);
            int ground = (int) Math.floor(getY());
            for (int y = ground; y <= ground + 5; y++) {
                BlockPos p = new BlockPos(x, y, z);
                var st = this.level().getBlockState(p);
                if (!st.isAir() && !st.canBeReplaced() && st.getFluidState().isEmpty() && enemyLand(p)) return p;
            }
        }
        return null;
    }

    private @Nullable LivingEntity enemyInRange(double range) {
        LivingEntity best = null;
        double bd = range * range;
        for (Citizen c : this.level().getEntitiesOfClass(Citizen.class, getBoundingBox().inflate(range), Citizen::isAlive)) {
            if (c.kingdomId() == null || c.sameKingdom(kingdomId)) continue;
            if (!(ammo < 0 && enemyLand(c.blockPosition())) && !Diplomacy.hostile(this.level().getServer(), kingdomId, c.kingdomId())) continue;
            double d = c.distanceToSqr(this);
            if (d < bd && d > kind().minRange * kind().minRange) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private void face(Vec3 at) {
        float yaw = (float) (Math.toDegrees(Math.atan2(at.z - getZ(), at.x - getX())) - 90.0);
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private void fire(ServerLevel level, Vec3 target) {
        Kind kind = kind();
        face(target);
        double yaw = Math.toRadians(getYRot() + 90.0);
        Vec3 fwd = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 from = kind == Kind.CANNON ? position().add(fwd.scale(1.4)).add(0, 1.0, 0) : position().add(fwd.scale(1.8)).add(0, 3.2, 0);
        Vec3 aimAt = kind == Kind.TREBUCHET ? target.add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3) : target;
        Vec3 v = SiegeShot.aim(from, aimAt, kind == Kind.TREBUCHET);
        SiegeShot shot = new SiegeShot(kind == Kind.CANNON ? ModContent.CANNONBALL_ENTITY.get() : ModContent.BOULDER.get(), level);
        shot.setOwner(this);
        shot.setPos(from);
        shot.setDeltaMovement(v);
        level.addFreshEntity(shot);
        if (kind == Kind.CANNON) {
            this.entityData.set(PITCH, (float) Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.7f);
        } else {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.HOSTILE, 2.5f, 0.5f);
        }
        this.entityData.set(ACTION, (int) level.getGameTime());
        if (ammo > 0) ammo--;
        cooldown = kind.reload;
    }

    private void bash(ServerLevel level, BlockPos wall) {
        face(Vec3.atCenterOf(wall));
        for (int dy = -1; dy <= 2; dy++) SiegeDamage.hit(level, wall.atY((int) Math.floor(getY()) + Math.max(0, dy)), 40f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(wall).inflate(1.2),
                e -> e != this && !(e instanceof Citizen c && c.sameKingdom(kingdomId)))) e.hurtServer(level, damageSources().mobAttack(this), 10f);
        level.playSound(null, wall, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.6f);
        this.entityData.set(ACTION, (int) level.getGameTime());
        cooldown = kind().reload;
    }

    /** Rolls toward the objective, then batters or bombards it. */
    private final class Work extends Goal {
        private @Nullable BlockPos target;
        private @Nullable LivingEntity targetMob;
        private int think;

        Work() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return dest != null || fireAt != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level)) return;
            if (cooldown > 0) cooldown--;
            Kind kind = kind();
            if (--think <= 0) {
                think = 20;
                targetMob = null;
                if (fireAt != null && !level.getBlockState(fireAt).isAir()) target = fireAt;
                else {
                    if (fireAt != null) fireAt = null;
                    target = dest == null ? null : obstacleToward(dest, kind == Kind.RAM ? 3.0 : kind.maxRange);
                    if (target == null && kind != Kind.RAM) targetMob = enemyInRange(kind.maxRange);
                }
            }
            Vec3 aim = target != null ? Vec3.atCenterOf(target) : targetMob != null && targetMob.isAlive() ? targetMob.position().add(0, 0.5, 0) : null;
            if (aim == null) {
                if (dest != null && distanceToSqr(Vec3.atCenterOf(dest)) > 8 * 8) roll(dest);
                else getNavigation().stop();
                return;
            }
            double dist = Math.sqrt((aim.x - getX()) * (aim.x - getX()) + (aim.z - getZ()) * (aim.z - getZ()));
            if (kind == Kind.RAM) {
                if (dist > 2.6) {
                    roll(BlockPos.containing(aim));
                    return;
                }
                getNavigation().stop();
                if (cooldown <= 0 && crewed() && target != null) bash(level, target);
                return;
            }
            if (dist > kind.maxRange - 2) {
                roll(BlockPos.containing(aim));
                return;
            }
            getNavigation().stop();
            face(aim);
            if (cooldown <= 0 && dist >= kind.minRange && crewed() && (ammo != 0)) fire(level, aim);
        }

        private void roll(BlockPos goal) {
            if (getNavigation().isDone() || tickCount % 40 == 0) {
                Vec3 g = Vec3.atBottomCenterOf(goal);
                double dx = g.x - getX(), dz = g.z - getZ(), len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                double leg = Math.min(len, 12);
                double tx = getX() + dx / len * leg, tz = getZ() + dz / len * leg;
                int ty = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
                if (!getNavigation().moveTo(tx, ty, tz, 1.0)) getMoveControl().setWantedPosition(tx, ty, tz, 1.0);
            }
        }
    }

    // ------------------------------------------------------------------ players

    public static boolean isAmmo(Kind kind, ItemStack s) {
        if (kind == Kind.CANNON) return s.is(ModContent.CANNONBALL.get());
        if (kind == Kind.TREBUCHET) return s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.ANDESITE)
                || s.is(Items.DIORITE) || s.is(Items.GRANITE);
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        Kingdom k = kingdom();
        Kind kind = kind();
        if (k != null && !k.isMember(player.getUUID())) {
            sp.sendOverlayMessage(Component.literal("A " + kind.title + " of ").append(Realm.title(k)));
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack held = player.getMainHandItem();
        if (isAmmo(kind, held) && ammo >= 0) {
            int take = Math.min(held.getCount(), 64 - ammo);
            ammo += take;
            held.shrink(take);
            sp.sendOverlayMessage(Component.literal("Loaded " + take + " shot (" + ammo + " in all).").withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            Realm.give(player, new ItemStack(itemFor(kind)));
            if (ammo > 0 && kind != Kind.RAM) Realm.give(player, new ItemStack(kind == Kind.CANNON ? ModContent.CANNONBALL.get() : Items.COBBLESTONE, ammo));
            discard();
            sp.sendOverlayMessage(Component.literal("You pack up the " + kind.title + ".").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        String what = kind == Kind.RAM ? "Mark a wall with the Command Baton in Siege mode and it rolls up and batters it."
                : (ammo == 0 ? "Load it with " + (kind == Kind.CANNON ? "Cannonballs" : "stone") + ", then " : "")
                + "mark a target with the Command Baton in Siege mode (range " + kind.minRange + "-" + kind.maxRange + " blocks).";
        sp.sendSystemMessage(Component.literal(kind.title + (kind == Kind.RAM ? "" : " (" + ammo + " shot loaded)") + ". ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(what + " It needs a crew: one of your people (or you) standing by. Sneak + right-click with an empty hand to pack it up.")
                        .withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS_SERVER;
    }

    public static Item itemFor(Kind kind) {
        return switch (kind) {
            case RAM -> ModContent.BATTERING_RAM_ITEM.get();
            case TREBUCHET -> ModContent.TREBUCHET_ITEM.get();
            case CANNON -> ModContent.CANNON_ITEM.get();
        };
    }

    // ------------------------------------------------------------------ the rest

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOOD_HIT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WOOD_BREAK; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() == null && !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            com.dominionmod.dominion.kingdom.Armies.onDeath(level, getUUID());
            spawnAtLocation(level, new ItemStack(Items.OAK_PLANKS, 4 + random.nextInt(5)));
            if (kind() == Kind.CANNON) spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 2));
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 1, 0.6, 1, 0.05);
        }
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (kingdomId != null) out.putString("Kingdom", kingdomId.toString());
        if (dest != null) out.putLong("Dest", dest.asLong());
        if (fireAt != null) out.putLong("FireAt", fireAt.asLong());
        out.putInt("Ammo", ammo);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.getString("Kingdom").ifPresent(s -> {
            try {
                kingdomId = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        });
        dest = in.getLong("Dest").map(BlockPos::of).orElse(null);
        fireAt = in.getLong("FireAt").map(BlockPos::of).orElse(null);
        ammo = in.getIntOr("Ammo", 0);
    }

}
