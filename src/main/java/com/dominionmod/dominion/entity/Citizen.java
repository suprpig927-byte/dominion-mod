package com.dominionmod.dominion.entity;

import com.dominionmod.dominion.entity.goal.DepositGoal;
import com.dominionmod.dominion.entity.goal.FarmGoal;
import com.dominionmod.dominion.entity.goal.FormationGoal;
import com.dominionmod.dominion.entity.goal.HoldGoal;
import com.dominionmod.dominion.entity.goal.LumberGoal;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import com.dominionmod.dominion.registry.ModContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A villager who has sworn to your kingdom. Their job comes from the tool you hand them; they wear
 * your kingdom's colors and whatever armor you give them, eat from the storehouse and pay taxes
 * (or draw wages, if they're soldiers).
 */
public class Citizen extends PathfinderMob implements RangedAttackMob, InventoryCarrier {
    private static final EntityDataAccessor<Integer> PRIMARY = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SECONDARY = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPEC = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHILD = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> FLAG = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.ITEM_STACK);

    public static final long DAY = 24000L;
    public static final int DESERT_AFTER_DAYS = 3;

    private static final String[] NAMES = {
            "Aldric", "Bram", "Cora", "Dain", "Edda", "Finn", "Greta", "Hilda", "Ivo", "Jory", "Kara", "Leif", "Mira", "Nils",
            "Odo", "Pell", "Quinn", "Rhea", "Sten", "Tova", "Ulric", "Vera", "Wyn", "Yara", "Arlo", "Bess", "Corin", "Dagny",
            "Elric", "Freya", "Gunnar", "Hale", "Ingrid", "Jarl", "Kestrel", "Linnea", "Magnus", "Nell", "Osric", "Piety"};

    private final SimpleContainer inventory = new SimpleContainer(12);
    /** A builder's building materials, kept apart from what they haul home. */
    private final SimpleContainer materials = new SimpleContainer(18);
    private @Nullable UUID kingdomId;
    private String baseName = "";
    private @Nullable BlockPos post;
    private Order order = Order.DUTY;
    private @Nullable BlockPos anchor;
    private @Nullable UUID commander;
    private int formationIndex, formationSize = 1;
    private long lastUpkeepDay = -1;
    private int hungryDays, unpaidDays;
    private Job lastJob = Job.UNASSIGNED;
    private int lastWorkTick;
    /** Children grow up when this reaches zero. */
    private int growTicks;
    public static final int GROW_UP_TICKS = 24000;
    private int castCooldown;

    public Citizen(EntityType<? extends Citizen> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.getNavigation().setCanOpenDoors(true);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PRIMARY, 14);
        builder.define(SECONDARY, 4);
        builder.define(LOOK, 0);
        builder.define(FLAG, ItemStack.EMPTY);
        builder.define(TYPE, "plains");
        builder.define(SPEC, -1);
        builder.define(CHILD, false);
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new com.dominionmod.dominion.entity.goal.GarrisonGoal(this));
        this.goalSelector.addGoal(5, new com.dominionmod.dominion.entity.goal.WorkshopGoal(this));
        this.goalSelector.addGoal(2, new RangedBowAttackGoal<Citizen>(this, 0.75, 20, 16.0f) {
            @Override public boolean canUse() { return job() == Job.ARCHER && hasAmmo() && !com.dominionmod.dominion.kingdom.Watchtowers.garrisoned(Citizen.this) && super.canUse(); }
            @Override public boolean canContinueToUse() { return job() == Job.ARCHER && hasAmmo() && !com.dominionmod.dominion.kingdom.Watchtowers.garrisoned(Citizen.this) && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.85, true) {
            @Override public boolean canUse() { return fightsHandToHand() && super.canUse(); }
            @Override public boolean canContinueToUse() { return fightsHandToHand() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(2, new com.dominionmod.dominion.entity.goal.WizardGoal(this));
        this.goalSelector.addGoal(2, new com.dominionmod.dominion.entity.goal.MusketeerGoal(this));
        this.goalSelector.addGoal(5, new com.dominionmod.dominion.entity.goal.ScholarGoal(this));
        this.goalSelector.addGoal(5, new com.dominionmod.dominion.entity.goal.SmithGoal(this));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 0.6, 0.8) {
            @Override public boolean canUse() { return !job().soldier && super.canUse(); }
        });
        this.goalSelector.addGoal(3, new FormationGoal(this));
        this.goalSelector.addGoal(4, new HoldGoal(this));
        this.goalSelector.addGoal(4, new com.dominionmod.dominion.entity.goal.PatrolGoal(this));
        this.goalSelector.addGoal(4, new com.dominionmod.dominion.entity.goal.MarchGoal(this));
        this.goalSelector.addGoal(1, new com.dominionmod.dominion.entity.goal.EnvoyGoal(this));
        this.goalSelector.addGoal(5, new FarmGoal(this));
        this.goalSelector.addGoal(5, new LumberGoal(this));
        this.goalSelector.addGoal(5, new com.dominionmod.dominion.entity.goal.BuildGoal(this));
        this.goalSelector.addGoal(4, new com.dominionmod.dominion.entity.goal.RepairGoal(this)); // ahead of building: walls first
        this.goalSelector.addGoal(3, new com.dominionmod.dominion.entity.goal.BattlementGoal(this));
        this.goalSelector.addGoal(4, new com.dominionmod.dominion.entity.goal.SleepGoal(this));
        this.goalSelector.addGoal(6, new DepositGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.45) {
            @Override public boolean canUse() { return order == Order.DUTY && super.canUse(); }
        });
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return job().soldier && super.canUse(); }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, true,
                (target, level) -> target instanceof Enemy && !(target instanceof Creeper && order == Order.RALLY)) {
            @Override public boolean canUse() { return job().soldier && super.canUse(); }
        });
        // At war: the enemy's soldiers, people and members are fair game.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target, level) -> isEnemy(target)) {
            @Override public boolean canUse() { return job().soldier && super.canUse(); }
        });
    }

    /** Someone this citizen's kingdom is at war with (a soldier or subject of theirs, or one of its members). */
    public boolean isEnemy(LivingEntity target) {
        if (kingdomId == null || !target.isAlive() || this.level().getServer() == null) return false;
        if (target instanceof Citizen c) return c.kingdomId() != null && com.dominionmod.dominion.kingdom.Diplomacy.hostile(this.level().getServer(), kingdomId, c.kingdomId());
        if (target instanceof com.dominionmod.dominion.entity.siege.SiegeEngine se)
            return se.kingdomId() != null && com.dominionmod.dominion.kingdom.Diplomacy.hostile(this.level().getServer(), kingdomId, se.kingdomId())
                    && distanceToSqr(se) < 24 * 24;
        if (target instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            var k = KingdomData.get(this.level().getServer()).byPlayer(p.getUUID());
            return k != null && com.dominionmod.dominion.kingdom.Diplomacy.hostile(this.level().getServer(), kingdomId, k.id);
        }
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        ItemStack arrows = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrows, power, bow);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (this.level() instanceof ServerLevel sl) {
            float bonus = com.dominionmod.dominion.kingdom.Industry.archeryBonus(sl, kingdom());
            if (com.dominionmod.dominion.kingdom.Watchtowers.garrisoned(this)) bonus += 0.25f;
            if (bonus > 0) arrow.setBaseDamageFromMob(power * (1 + bonus));
        }
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (this.level() instanceof ServerLevel level) {
            Projectile.spawnProjectileUsingShoot(arrow, level, arrows, dx, dy + dist * 0.2, dz, 1.6f, 4.0f);
        }
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        spendAmmo();
    }

    // ------------------------------------------------------------------ ammunition in the field

    public static final int MAX_AMMO = 40;
    private int ammo = MAX_AMMO;
    private boolean warnedDry;

    private boolean shoots() {
        Job j = job();
        return j == Job.ARCHER || j == Job.MUSKETEER;
    }

    /** Away from home (more than a long bowshot from the Town Hall), a player kingdom's archers and musketeers live on what they carry. */
    public boolean inField() {
        Kingdom k = kingdom();
        if (k == null || k.wild || k.hall.isEmpty()) return false;
        if (!k.hallDim.equals(KingdomData.dim(this.level()))) return true;
        return k.hall.get().distSqr(blockPosition()) > Realm.HOUSING_RADIUS * Realm.HOUSING_RADIUS;
    }

    public boolean hasAmmo() {
        return !shoots() || ammo > 0 || !inField();
    }

    public int ammo() { return ammo; }

    /** Test hook: sets the quiver/shot count directly. */
    public void debugSetAmmo(int n) { this.ammo = Math.max(0, Math.min(MAX_AMMO, n)); }

    public void spendAmmo() {
        if (!shoots() || !inField()) return;
        ammo = Math.max(0, ammo - 1);
        if (ammo == 0 && !warnedDry && this.level() instanceof ServerLevel level && kingdom() != null) {
            warnedDry = true;
            tellKingdom(level, kingdom(), Component.literal(baseName + " the " + job().title + " is out of "
                    + (job() == Job.MUSKETEER ? "shot" : "arrows") + " and draws a knife. A supply cart nearby would keep them stocked.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Takes up to {@code n} rounds; returns how many were taken. */
    public int resupply(int n) {
        int take = Math.min(n, MAX_AMMO - ammo);
        ammo += take;
        if (ammo > 0) warnedDry = false;
        return take;
    }

    public boolean needsAmmo() {
        return shoots() && ammo < MAX_AMMO - 8;
    }

    /** Melee soldiers, and archers or musketeers who've run dry. */
    private boolean fightsHandToHand() {
        return job().melee() || shoots() && !hasAmmo();
    }

    // ------------------------------------------------------------------ cavalry

    private @Nullable UUID steed;

    public @Nullable UUID steed() { return steed; }
    public void setSteed(@Nullable UUID id) { this.steed = id; }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        float bonus = Cavalry.chargeBonus(this);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        var id = com.dominionmod.dominion.DominionMod.id("charge");
        if (bonus > 1.5f && dmg != null) dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, bonus,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        boolean hit = super.doHurtTarget(level, target);
        if (dmg != null && dmg.hasModifier(id)) dmg.removeModifier(id);
        if (hit && bonus > 1.5f && target instanceof LivingEntity le) {
            le.knockback(1.4, getX() - le.getX(), getZ() - le.getZ(), damageSources().mobAttack(this), bonus);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, le.getX(), le.getY() + 1, le.getZ(), 10, 0.4, 0.4, 0.4, 0.2);
        }
        return hit;
    }

    /** When one of a kingdom's people is attacked, its soldiers nearby turn on the attacker. */
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && kingdomId != null && source.getEntity() instanceof LivingEntity attacker && attacker.isAlive()
                && !(attacker instanceof Citizen c && c.sameKingdom(kingdomId))
                && !(attacker instanceof Player p && kingdom() != null && kingdom().isMember(p.getUUID()))) {
            for (Citizen ally : level.getEntitiesOfClass(Citizen.class, getBoundingBox().inflate(24),
                    c -> c != this && c.sameKingdom(kingdomId) && c.job().soldier && c.getTarget() == null)) {
                ally.setTarget(attacker);
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level) || this.tickCount % 20 != 0) return;

        Job job = job();
        if (job != lastJob) onJobChanged(level, job);

        long day = level.getGameTime() / DAY;
        if (lastUpkeepDay < 0) lastUpkeepDay = day;
        if (day > lastUpkeepDay) upkeep(level, day);

        if (isSleeping() && (level.isBrightOutside() || order != Order.DUTY || enemyNear(level))) stopSleeping();
        if (this.tickCount % 100 == 0) refreshFromKingdom(level);
        if (job.worker()) pickUpNearbyItems(level);
        if (job.soldier) commanderBonus(level);
        if (!inField()) ammo = MAX_AMMO;
        if (com.dominionmod.dominion.kingdom.Caravans.onRoad(isPassenger() && getVehicle() != null ? getVehicle() : this)) {
            addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false));
            if (getVehicle() instanceof LivingEntity mount) mount.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false));
        }
        Cavalry.tick(level, this);
        if (isChild() && (growTicks -= 20) <= 0) growUp(level);
        if (job == Job.MONK && this.tickCount % 100 == 0) monkBlessing(level);
        if (job == Job.BARBARIAN && getHealth() < getMaxHealth() / 2 && getTarget() != null) {
            addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1, true, true));
            addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, true));
        }

        // A rallied soldier doesn't chase things far from their commander.
        if (order == Order.RALLY && getTarget() != null) {
            Player cmd = commanderPlayer();
            if (cmd == null || getTarget().distanceToSqr(cmd) > 20 * 20) setTarget(null);
        }
        if (order == Order.PATROL && getTarget() != null && !patrol.isEmpty()
                && getTarget().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(patrol.get(patrolIndex))) > 24 * 24) {
            setTarget(null);
        }
        if (order == Order.HOLD && anchor != null && getTarget() != null && getTarget().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(anchor)) > 14 * 14) {
            setTarget(null);
        }
    }

    private void onJobChanged(ServerLevel level, Job job) {
        lastJob = job;
        if (post == null) post = blockPosition();
        refreshName();
        KingdomData d = Realm.data(level);
        Kingdom k = d.byId(kingdomId);
        if (k != null && !temporary) Realm.addCitizen(k, getUUID(), job, d);
        if (!job.soldier) setTarget(null);
    }

    /** Monks heal and steady their kingdom's people nearby. */
    private void monkBlessing(ServerLevel level) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8),
                e -> e.isAlive() && (e instanceof Citizen c && c.sameKingdom(kingdomId) || e instanceof Player p && kingdom() != null && kingdom().isMember(p.getUUID())))) {
            if (e.getHealth() < e.getMaxHealth()) e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true));
            if (e instanceof Citizen c && c.getTarget() != null) e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, true, true));
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(), 6, 2.5, 0.5, 2.5, 0);
    }

    // ------------------------------------------------------------------ children and classes

    public boolean isChild() { return this.entityData.get(CHILD); }

    /** Makes this a newborn: small, can't work, grows up in a Minecraft day. */
    public void makeChild() {
        this.entityData.set(CHILD, true);
        this.growTicks = GROW_UP_TICKS;
        applyStats();
    }

    private void growUp(ServerLevel level) {
        this.entityData.set(CHILD, false);
        applyStats();
        refreshName();
        Kingdom k = kingdom();
        if (k != null) tellKingdom(level, k, Component.literal(baseName + " has grown up and is ready to work.").withStyle(ChatFormatting.GREEN));
    }

    /** The University class this citizen was trained in, if any. */
    public @Nullable Job specialization() {
        int i = this.entityData.get(SPEC);
        return i < 0 ? null : Job.byId(i);
    }

    public void train(Job cls) {
        this.entityData.set(SPEC, cls.ordinal());
        if (cls == Job.WIZARD) {
            ItemStack old = getMainHandItem();
            if (!old.isEmpty() && this.level() instanceof ServerLevel level) spawnAtLocation(level, old.copy());
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.dominionmod.dominion.registry.ModContent.SPELL_BOOK.get()));
        }
        applyStats();
        if (this.level() instanceof ServerLevel level) onJobChanged(level, job());
    }

    /** Health, damage and size for children and classes. */
    private void applyStats() {
        Job j = job();
        setBase(Attributes.SCALE, isChild() ? 0.5 : 1.0);
        setBase(Attributes.MAX_HEALTH, j == Job.BARBARIAN ? 40 : j == Job.MONK ? 30 : isChild() ? 12 : 24);
        setBase(Attributes.ATTACK_DAMAGE, j == Job.BARBARIAN ? 5 : j == Job.MONK ? 4 : 1);
        setBase(Attributes.MOVEMENT_SPEED, j == Job.BARBARIAN ? 0.55 : 0.5);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        var inst = getAttribute(attr);
        if (inst != null && inst.getBaseValue() != v) inst.setBaseValue(v);
    }

    public boolean castReady() { return castCooldown <= 0; }
    public void tickCast() { if (castCooldown > 0) castCooldown--; }
    public void startCast(int ticks) { castCooldown = ticks; }

    private boolean enemyNear(ServerLevel level) {
        return !level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10), m -> m instanceof Enemy && m.isAlive()).isEmpty();
    }

    /** Keeps colors and flag in step with the kingdom, and makes sure they have a bed. */
    private void refreshFromKingdom(ServerLevel level) {
        Kingdom k = kingdom();
        if (k == null || temporary) return;
        if (getPrimary() != k.primary) this.entityData.set(PRIMARY, k.primary);
        if (getSecondary() != k.secondary) this.entityData.set(SECONDARY, k.secondary);
        if (!ItemStack.matches(this.entityData.get(FLAG), k.flag)) this.entityData.set(FLAG, k.flag.copy());
        if (!baseName.isEmpty() && !baseName.equals(k.names.get(getUUID()))) {
            k.names.put(getUUID(), baseName);
            Realm.data(level).markChanged();
        }
        Realm.ensureBed(level, k, this);
    }

    /** The bed this citizen sleeps in, if they have one. */
    public @Nullable BlockPos home() {
        Kingdom k = kingdom();
        return k == null ? null : k.bedOf(getUUID());
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSleeping();
    }

    /** Soldiers fight harder near any member of their kingdom. */
    private void commanderBonus(ServerLevel level) {
        Kingdom k = kingdom();
        if (k == null) return;
        for (ServerPlayer p : level.players()) {
            if (k.isMember(p.getUUID()) && p.isAlive() && p.distanceToSqr(this) < 16 * 16) {
                addEffect(new MobEffectInstance(MobEffects.STRENGTH, 50, 0, true, false));
                return;
            }
        }
    }

    private void pickUpNearbyItems(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(2.5, 1.0, 2.5))) {
            ItemStack s = item.getItem();
            if (item.hasPickUpDelay() || !wantsItem(s) || !inventory.canAddItem(s)) continue;
            ItemStack left = inventory.addItem(s.copy());
            take(item, s.getCount() - left.getCount());
            if (left.isEmpty()) item.discard();
            else item.setItem(left);
        }
    }

    private static boolean wantsItem(ItemStack s) {
        return s.is(ItemTags.SAPLINGS) || s.is(ItemTags.LOGS) || s.is(Items.STICK) || s.is(Items.APPLE) || s.is(Items.WHEAT)
                || s.is(Items.BEETROOT) || isPlantable(s);
    }

    public static boolean isPlantable(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    // ------------------------------------------------------------------ daily upkeep

    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private void upkeep(ServerLevel level, long day) {
        Kingdom k = kingdom();
        lastUpkeepDay = day;
        if (k == null || k.wild || temporary) return; // wild kingdoms look after their own
        var store = Storehouse.containers(level, k);
        if (store.isEmpty() && k.hall.isPresent() && !level.isLoaded(k.hall.get())) return; // can't reach the town right now

        Job job = job();
        ItemStack meal = Storehouse.take(store, s -> s.has(DataComponents.FOOD), 1);
        if (meal.isEmpty()) hungryDays++;
        else {
            hungryDays = 0;
            if (getHealth() < getMaxHealth()) heal(6);
        }
        if (job.dailyCoins > 0) {
            ItemStack left = Storehouse.insert(store, new ItemStack(ModContent.GOLD_COIN.get(), job.dailyCoins));
            if (!left.isEmpty() && k.hall.isPresent()) spawnAtLocation(level, left);
        } else if (job.dailyCoins < 0) {
            ItemStack pay = Storehouse.take(store, s -> s.is(ModContent.GOLD_COIN.get()), -job.dailyCoins);
            if (pay.getCount() >= -job.dailyCoins) unpaidDays = 0;
            else unpaidDays++;
        }

        if (hungryDays >= DESERT_AFTER_DAYS) {
            desert(level, k, "went hungry for " + hungryDays + " days");
            return;
        }
        if (unpaidDays >= DESERT_AFTER_DAYS) {
            desert(level, k, "wasn't paid for " + unpaidDays + " days");
            return;
        }
        if ((hungryDays > 0 || unpaidDays > 0) && !Long.valueOf(day).equals(WARNED.get(k.id))) {
            WARNED.put(k.id, day);
            String why = hungryDays > 0 && unpaidDays > 0 ? "hungry and unpaid" : hungryDays > 0 ? "hungry" : "unpaid";
            tellKingdom(level, k, Component.literal("Some of your citizens are " + why
                    + ". Keep food and Gold Coins in chests next to your Town Hall, or they will leave.").withStyle(ChatFormatting.RED));
        }
    }

    /** Debug: run one day's upkeep right now. */
    public void debugUpkeep() {
        if (this.level() instanceof ServerLevel level) upkeep(level, lastUpkeepDay + 1);
    }

    public void markWorked() { lastWorkTick = tickCount; }
    public int idleTicks() { return tickCount - lastWorkTick; }

    public int hungryDays() { return hungryDays; }
    public int unpaidDays() { return unpaidDays; }

    public static void tellKingdom(ServerLevel level, Kingdom k, Component msg) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) if (k.isMember(p.getUUID())) p.sendSystemMessage(msg);
    }

    /** Leaves the kingdom and goes back to being an ordinary villager. */
    public void desert(ServerLevel level, @Nullable Kingdom k, String why) {
        if (k != null) tellKingdom(level, k, Component.literal(baseName + " the " + job().title + " " + why + " and left your kingdom.")
                .withStyle(ChatFormatting.RED));
        dropEverything(level);
        Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.CONVERSION);
        if (v != null) {
            var type = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE)
                    .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TYPE,
                            net.minecraft.resources.Identifier.withDefaultNamespace(getVillagerType())));
            type.ifPresent(t -> v.setVillagerData(v.getVillagerData().withType(t)));
            v.snapTo(getX(), getY(), getZ(), getYRot(), getXRot());
            level.addFreshEntity(v);
        }
        Realm.removeCitizen(level, kingdomId, getUUID());
        kingdomId = null;
        discard();
    }

    private void dropEverything(ServerLevel level) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty() && (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)) {
                spawnAtLocation(level, s.copy());
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(level, s);
        for (ItemStack s : materials.removeAllItems()) spawnAtLocation(level, s);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            Kingdom k = kingdom();
            if (k != null && !temporary && !k.wild) tellKingdom(level, k, Component.literal(baseName + " the " + job().title + " has died.").withStyle(ChatFormatting.GRAY));
            for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(level, s);
            for (ItemStack s : materials.removeAllItems()) spawnAtLocation(level, s);
            if (k != null && k.wild && job().soldier) spawnAtLocation(level, new ItemStack(ModContent.GOLD_COIN.get(), 1 + this.random.nextInt(3)));
            // murdering an envoy is an act of war
            if (k != null && onEnvoyErrand() && source.getEntity() instanceof Player killer) {
                Kingdom theirs = Realm.data(level).byPlayer(killer.getUUID());
                if (theirs != null && theirs != k) {
                    com.dominionmod.dominion.kingdom.Diplomacy.declareWar(level, k, theirs, "their envoy was murdered");
                    com.dominionmod.dominion.kingdom.Rivals.onWarDeclared(level, k, theirs);
                }
            }
            if (!temporary) Realm.removeCitizen(level, kingdomId, getUUID());
            com.dominionmod.dominion.kingdom.Armies.onDeath(level, getUUID());
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isEnvoy() && !envoySpoken() && envoyFor != null && envoyFor.equals(player.getUUID())) {
            if (this.level() instanceof ServerLevel) deliverEnvoy();
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModContent.COMMAND_BATON.get())) return InteractionResult.PASS; // the baton handles it
        if (!(this.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Kingdom k = kingdom();
        if (k == null || !k.isMember(player.getUUID())) {
            player.sendOverlayMessage(Component.literal(baseName + " serves " + (k == null ? "no one" : k.name) + "."));
            return InteractionResult.CONSUME;
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        if (isChild()) {
            if (held.isEmpty()) player.sendSystemMessage(status());
            else player.sendOverlayMessage(Component.literal(baseName + " is too young to work. They'll grow up in a day or so."));
            return InteractionResult.SUCCESS_SERVER;
        }
        // Leading a saddled horse? The soldier takes it.
        if (held.isEmpty() && !player.isShiftKeyDown() && Cavalry.giveLedHorse(player, this)) return InteractionResult.SUCCESS_SERVER;
        // Armor goes on, a shield goes in the off hand, a tool or weapon becomes their job.
        Equippable eq = held.get(DataComponents.EQUIPPABLE);
        if (!held.isEmpty() && eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            swapInto(player, hand, eq.slot());
            player.sendOverlayMessage(Component.literal(baseName + " puts on the " + getItemBySlot(eq.slot()).getHoverName().getString() + "."));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
            swapInto(player, hand, EquipmentSlot.OFFHAND);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (Job.of(held) == Job.BLACKSMITH && !Realm.data(level).progress(k).has("blacksmithing")) {
            player.sendSystemMessage(Realm.err("Research Blacksmithing at a Research Table before you can train a Blacksmith."));
            return InteractionResult.SUCCESS_SERVER;
        }
        Job spec = specialization();
        if (spec != null && Job.of(held) != Job.UNASSIGNED) {
            // Trained classes keep their calling; they'll still take a better weapon.
            if (spec == Job.WIZARD || spec == Job.ENGINEER) {
                player.sendOverlayMessage(Component.literal(baseName + " is a " + spec.title + " and doesn't need that."));
                return InteractionResult.SUCCESS_SERVER;
            }
            swapInto(player, hand, EquipmentSlot.MAINHAND);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (Job.of(held) != Job.UNASSIGNED) {
            swapInto(player, hand, EquipmentSlot.MAINHAND);
            this.post = blockPosition();
            this.order = Order.DUTY;
            Job job = job();
            onJobChanged(level, job);
            player.sendSystemMessage(Component.literal(baseName + " is now a " + job.title + ". ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(jobHint(job)).withStyle(ChatFormatting.GRAY)));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            // Take back their tool/weapon and armor.
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = getItemBySlot(slot);
                if (!s.isEmpty()) {
                    Realm.give(player, s.copy());
                    setItemSlot(slot, ItemStack.EMPTY);
                }
            }
            if (steed != null || isPassenger()) Cavalry.release(this);
            player.sendOverlayMessage(Component.literal(baseName + " hands back their gear."));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.isEmpty()) {
            player.sendSystemMessage(status());
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    private static String jobHint(Job job) {
        return switch (job) {
            case FARMER -> "They'll harvest and replant crops around this spot.";
            case LUMBERJACK -> "They'll fell and replant trees around this spot.";
            case MUSKETEER -> "They'll guard this spot and fire on enemies from range. No bullets needed: the kingdom supplies powder.";
            case MILITIA, ARCHER -> "They'll guard this spot. Use the Command Baton to lead them.";
            case BUILDER -> "They'll build your construction sites. Get blueprints from the Build tab of your Realm screen (K).";
            case SCHOLAR -> "They'll work at your Research Table, speeding up research.";
            case BLACKSMITH -> "They'll work at an anvil near here: smelting ore and forging armor for your soldiers.";
            default -> "";
        };
    }

    private void swapInto(Player player, InteractionHand hand, EquipmentSlot slot) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack old = getItemBySlot(slot);
        setItemSlot(slot, held.copyWithCount(1));
        setGuaranteedDrop(slot);
        held.shrink(1);
        if (!old.isEmpty()) Realm.give(player, old);
        playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1f, 1f);
    }

    public Component status() {
        if (isChild()) {
            return Component.literal(baseName + ", a child").withStyle(s -> s.withColor(colorOf(getPrimary())).withBold(true))
                    .append(Component.literal(String.format("  grows up in about %d minutes", Math.max(1, growTicks / 1200))).withStyle(ChatFormatting.GRAY));
        }
        String mood = hungryDays > 0 ? "hungry" : "fed";
        if (job().soldier) mood += unpaidDays > 0 ? ", unpaid" : ", paid";
        BlockPos bed = home();
        mood += bed == null ? ", no bed" : ", sleeps at " + bed.getX() + " " + bed.getY() + " " + bed.getZ();
        mood += Cavalry.describe(this);
        if (shoots() && inField()) mood += ", " + ammo + " rounds";
        String orderText = switch (order) {
            case RALLY -> "rallied to their commander";
            case HOLD -> "holding position";
            case PATROL -> "patrolling a route of " + patrol.size() + " waypoints";
            case MARCH -> "on campaign";
            default -> job().worker() ? "working" : "on duty";
        };
        return Component.literal(baseName + " the " + job().title).withStyle(s -> s.withColor(colorOf(getPrimary())).withBold(true))
                .append(Component.literal(String.format("  %.0f/%.0f HP, %s, %s", getHealth(), getMaxHealth(), mood, orderText))
                        .withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ kingdom

    /** Merchants and hired guards: wear a kingdom's colors without joining its rolls (no bed, no wages, gone after the trip). */
    private boolean temporary;

    public void joinTemporarily(Kingdom k, String title) {
        this.kingdomId = k.id;
        this.temporary = true;
        this.entityData.set(PRIMARY, k.primary);
        this.entityData.set(SECONDARY, k.secondary);
        this.entityData.set(LOOK, this.random.nextInt(4));
        this.entityData.set(FLAG, k.flag.copy());
        if (baseName.isEmpty()) baseName = NAMES[this.random.nextInt(NAMES.length)];
        setCustomName(Component.literal(baseName + " (" + title + ")").withStyle(s -> s.withColor(colorOf(getPrimary()))));
        setCustomNameVisible(false);
    }

    public boolean temporary() { return temporary; }

    public void joinKingdom(Kingdom k, ServerLevel level) {
        this.kingdomId = k.id;
        this.entityData.set(PRIMARY, k.primary);
        this.entityData.set(SECONDARY, k.secondary);
        this.entityData.set(LOOK, this.random.nextInt(4));
        this.entityData.set(FLAG, k.flag.copy());
        if (baseName.isEmpty()) baseName = NAMES[this.random.nextInt(NAMES.length)];
        this.post = blockPosition();
        this.lastUpkeepDay = level.getGameTime() / DAY;
        Realm.addCitizen(k, getUUID(), job(), Realm.data(level));
        k.names.put(getUUID(), baseName);
        Realm.ensureBed(level, k, this);
        refreshName();
    }

    private void refreshName() {
        if (temporary) return;
        setCustomName(Component.literal(baseName + " (" + (isChild() ? "Child" : job().title) + ")").withStyle(s -> s.withColor(colorOf(getPrimary()))));
        setCustomNameVisible(false);
    }

    private static int colorOf(int dye) {
        return net.minecraft.world.item.DyeColor.byId(dye).getTextColor();
    }

    public @Nullable Kingdom kingdom() {
        if (kingdomId == null || this.level().getServer() == null) return null;
        return KingdomData.get(this.level().getServer()).byId(kingdomId);
    }

    public @Nullable UUID kingdomId() { return kingdomId; }
    public boolean sameKingdom(@Nullable UUID other) { return kingdomId != null && kingdomId.equals(other); }

    public Job job() {
        if (isChild()) return Job.UNASSIGNED;
        Job spec = specialization();
        if (spec != null && spec.trade()) {
            // a tool in hand (hoe, sword, bow...) takes them off the workshop's books
            Job held = Job.of(getMainHandItem());
            return held != Job.UNASSIGNED ? held : spec;
        }
        return spec != null ? spec : Job.of(getMainHandItem());
    }

    /** Hired by a workshop (or let go, with null). */
    public void hire(@Nullable Job trade) {
        this.entityData.set(SPEC, trade == null ? -1 : trade.ordinal());
        applyStats();
        if (this.level() instanceof ServerLevel level) onJobChanged(level, job());
    }
    public int getPrimary() { return this.entityData.get(PRIMARY); }
    public int getSecondary() { return this.entityData.get(SECONDARY); }
    public int getLook() { return this.entityData.get(LOOK); }
    public ItemStack getFlag() { return this.entityData.get(FLAG); }
    public String getVillagerType() { return this.entityData.get(TYPE); }
    public void setVillagerTypeForDebug(String type) { this.entityData.set(TYPE, type); }

    /** Keeps the look of the villager they were: desert villagers stay in desert clothes, and so on. */
    public void copyLookFrom(Villager v) {
        v.getVillagerData().type().unwrapKey().ifPresent(k -> this.entityData.set(TYPE, k.identifier().getPath()));
        if (v.hasCustomName() && v.getCustomName() != null) baseName = v.getCustomName().getString();
    }
    public SimpleContainer materials() { return materials; }
    public String baseName() { return baseName; }

    // ------------------------------------------------------------------ orders

    public Order order() { return order; }
    public @Nullable BlockPos anchor() { return anchor; }
    public BlockPos post() { return post != null ? post : blockPosition(); }
    public void setPost(BlockPos p) { this.post = p.immutable(); }
    public int formationIndex() { return formationIndex; }
    public int formationSize() { return formationSize; }

    public void rally(UUID commander, int index, int size) {
        this.order = Order.RALLY;
        this.commander = commander;
        this.formationIndex = index;
        this.formationSize = Math.max(1, size);
        this.anchor = null;
    }

    public void hold(BlockPos at) {
        this.order = Order.HOLD;
        this.anchor = at.immutable();
        this.commander = null;
        getNavigation().stop();
    }

    private final java.util.List<BlockPos> patrol = new java.util.ArrayList<>();
    private int patrolIndex;

    /** Walk this route, waypoint to waypoint, looping forever (or until given another order). */
    public void patrol(java.util.List<BlockPos> route) {
        this.patrol.clear();
        for (BlockPos p : route) this.patrol.add(p.immutable());
        this.order = Order.PATROL;
        this.anchor = null;
        this.commander = null;
        // start at the nearest waypoint
        double best = Double.MAX_VALUE;
        for (int i = 0; i < patrol.size(); i++) {
            double d = distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(patrol.get(i)));
            if (d < best) {
                best = d;
                patrolIndex = i;
            }
        }
    }

    /** Set out across country for {@code dest}, on campaign. */
    public void march(BlockPos dest) {
        this.order = Order.MARCH;
        this.anchor = dest.immutable();
        this.commander = null;
        this.patrol.clear();
    }

    // envoys: carry an offer to a member of another kingdom, then go home
    private @Nullable UUID envoyFor;
    private int envoyOffer = -1;
    private int envoyTicks;

    public void becomeEnvoy(UUID player, int offer) {
        this.envoyFor = player;
        this.envoyOffer = offer;
        this.envoyTicks = 0;
        setCustomNameVisible(true);
        setCustomName(Component.literal(baseName + ", Envoy of " + (kingdom() == null ? "?" : kingdom().name))
                .withStyle(s -> s.withColor(colorOf(getPrimary()))));
    }

    public boolean isEnvoy() { return envoyOffer >= 0; }
    /** Any envoy business at all: on the way, waiting after speaking, or left over from before a reload. */
    public boolean onEnvoyErrand() { return envoyOffer != -1; }
    public boolean envoyStale() { return envoyOffer == -3; }
    public @Nullable UUID envoyFor() { return envoyFor; }
    public int envoyOffer() { return envoyOffer; }
    public int envoyTicks() { return envoyTicks; }
    public void tickEnvoy() { envoyTicks++; }

    /** The envoy has spoken (or given up): they say their piece once. */
    public void deliverEnvoy() {
        if (envoyOffer < 0 || !(this.level() instanceof ServerLevel level)) return;
        var offer = com.dominionmod.dominion.kingdom.Diplomacy.offer(envoyOffer);
        if (offer != null) com.dominionmod.dominion.kingdom.Diplomacy.present(level, offer, baseName + ", envoy of ");
        envoyOffer = -2; // spoken; now waiting to leave
        envoyTicks = 0;
    }

    public boolean envoySpoken() { return envoyOffer == -2; }

    public java.util.List<BlockPos> patrolRoute() { return patrol; }
    public int patrolIndex() { return patrolIndex; }
    public void nextWaypoint() { if (!patrol.isEmpty()) patrolIndex = (patrolIndex + 1) % patrol.size(); }

    public void returnToDuty() {
        this.patrol.clear();
        this.order = Order.DUTY;
        this.anchor = null;
        this.commander = null;
    }

    public @Nullable Player commanderPlayer() {
        if (commander == null) return null;
        Player p = level().getPlayerByUUID(commander);
        return p != null && p.isAlive() && p.level() == level() ? p : null;
    }

    // ------------------------------------------------------------------ inventory

    @Override
    public SimpleContainer getInventory() { return inventory; }

    /** Items they're hauling to the storehouse (everything except seeds/saplings they keep for planting). */
    public int haulCount() {
        int n = 0;
        for (ItemStack s : inventory.getItems()) if (!s.isEmpty() && !keepsForWork(s)) n += s.getCount();
        return n;
    }

    public boolean inventoryFull() {
        for (ItemStack s : inventory.getItems()) if (s.isEmpty()) return false;
        return true;
    }

    public boolean keepsForWork(ItemStack s) {
        Job job = job();
        return job == Job.FARMER && isPlantable(s) || job == Job.LUMBERJACK && s.is(ItemTags.SAPLINGS);
    }

    public void stash(ServerLevel level, ItemStack s) {
        ItemStack left = inventory.addItem(s);
        if (!left.isEmpty()) spawnAtLocation(level, left);
    }

    // ------------------------------------------------------------------ misc

    @Override
    public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (kingdomId != null) out.putString("Kingdom", kingdomId.toString());
        out.putString("BaseName", baseName);
        if (post != null) out.putLong("Post", post.asLong());
        out.putInt("Order", order.ordinal());
        if (anchor != null) out.putLong("Anchor", anchor.asLong());
        if (!patrol.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (BlockPos p : patrol) sb.append(sb.length() == 0 ? "" : ",").append(p.asLong());
            out.putString("Patrol", sb.toString());
            out.putInt("PatrolIndex", patrolIndex);
        }
        out.putLong("LastUpkeep", lastUpkeepDay);
        out.putInt("Hungry", hungryDays);
        out.putInt("Unpaid", unpaidDays);
        out.putInt("Primary", getPrimary());
        out.putInt("Secondary", getSecondary());
        out.putInt("Look", getLook());
        out.putString("VillagerType", getVillagerType());
        out.putInt("Spec", this.entityData.get(SPEC));
        out.putBoolean("Child", isChild());
        out.putInt("GrowTicks", growTicks);
        out.putBoolean("Envoy", envoyOffer != -1 || temporary);
        out.putInt("Ammo", ammo);
        if (steed != null) out.putString("Steed", steed.toString());
        writeInventoryToTag(out);
        materials.storeAsItemList(out.list("Materials", ItemStack.CODEC));
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
        baseName = in.getStringOr("BaseName", "");
        in.getLong("Post").ifPresent(l -> post = BlockPos.of(l));
        order = Order.byId(in.getIntOr("Order", 0));
        if (order == Order.RALLY) order = Order.DUTY; // the commander may be gone after a reload
        in.getLong("Anchor").ifPresent(l -> anchor = BlockPos.of(l));
        if (order == Order.HOLD && anchor == null) order = Order.DUTY;
        patrol.clear();
        for (String part : in.getStringOr("Patrol", "").split(",")) {
            try {
                if (!part.isEmpty()) patrol.add(BlockPos.of(Long.parseLong(part)));
            } catch (NumberFormatException ignored) {
            }
        }
        patrolIndex = patrol.isEmpty() ? 0 : Math.floorMod(in.getIntOr("PatrolIndex", 0), patrol.size());
        if (order == Order.PATROL && patrol.isEmpty()) order = Order.DUTY;
        lastUpkeepDay = in.getLongOr("LastUpkeep", -1);
        hungryDays = in.getIntOr("Hungry", 0);
        unpaidDays = in.getIntOr("Unpaid", 0);
        this.entityData.set(PRIMARY, in.getIntOr("Primary", 14));
        this.entityData.set(SECONDARY, in.getIntOr("Secondary", 4));
        this.entityData.set(LOOK, in.getIntOr("Look", 0));
        this.entityData.set(TYPE, in.getStringOr("VillagerType", "plains"));
        this.entityData.set(SPEC, in.getIntOr("Spec", -1));
        this.entityData.set(CHILD, in.getBooleanOr("Child", false));
        this.growTicks = in.getIntOr("GrowTicks", 0);
        if (in.getBooleanOr("Envoy", false)) this.envoyOffer = -3; // an envoy from before a reload: leaves at once
        this.ammo = in.getIntOr("Ammo", MAX_AMMO);
        in.getString("Steed").ifPresent(st -> {
            try {
                steed = UUID.fromString(st);
            } catch (IllegalArgumentException ignored) {
            }
        });
        if (order == Order.MARCH && anchor == null) order = Order.DUTY;
        applyStats();
        readInventoryFromTag(in);
        in.list("Materials", ItemStack.CODEC).ifPresent(list -> materials.fromItemList(list));
        lastJob = job();
    }

    /** Used by the deposit goal and upkeep: everything this citizen should haul home. */
    public List<ItemStack> takeHaul() {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && !keepsForWork(s)) out.add(inventory.removeItemNoUpdate(i));
        }
        return out;
    }
}
