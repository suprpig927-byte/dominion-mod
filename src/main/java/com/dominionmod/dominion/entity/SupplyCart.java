package com.dominionmod.dominion.entity;

import com.dominionmod.dominion.entity.wild.BreedHorse;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import com.dominionmod.dominion.registry.ModContent;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A two-wheeled supply wagon pulled by a Shetland pony. Load it with arrows, shot and food (or let
 * it stock up at the Town Hall by itself); the pony follows your soldiers into the field, and any
 * soldier near the wagon refills their quiver and eats when hurt.
 */
public class SupplyCart extends Entity {
    private static final EntityDataAccessor<Integer> PONY = SynchedEntityData.defineId(SupplyCart.class, EntityDataSerializers.INT);
    /** How far behind the pony the cart rides, in blocks. */
    public static final double HITCH = 2.1;

    private final SimpleContainer cargo = new SimpleContainer(27);
    private @Nullable UUID ponyId;
    private @Nullable UUID kingdomId;

    public SupplyCart(EntityType<? extends SupplyCart> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PONY, -1);
    }

    /** The pony's network id (client side, for drawing the cart right behind it). */
    public int ponyNetworkId() {
        return this.entityData.get(PONY);
    }

    public SimpleContainer cargo() { return cargo; }

    public @Nullable UUID kingdomId() { return kingdomId; }

    public void hitch(BreedHorse pony, UUID kingdom) {
        this.ponyId = pony.getUUID();
        this.kingdomId = kingdom;
        this.entityData.set(PONY, pony.getId());
        pony.setCartId(getUUID());
        pony.setKingdomId(kingdom);
        follow(pony);
    }

    private void follow(Entity pony) {
        double yaw = Math.toRadians(pony.getYRot());
        double scale = pony instanceof BreedHorse b ? b.breed.scale : 1.0;
        Vec3 behind = pony.position().add(Math.sin(yaw) * HITCH * scale / 0.62, 0, -Math.cos(yaw) * HITCH * scale / 0.62);
        setPos(behind.x, pony.getY(), behind.z);
        setYRot(pony.getYRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        Entity pony = ponyId == null ? null : level.getEntity(ponyId);
        if (pony != null && pony.isAlive()) {
            follow(pony);
            if (this.entityData.get(PONY) != pony.getId()) this.entityData.set(PONY, pony.getId());
        } else {
            if (this.entityData.get(PONY) != -1) this.entityData.set(PONY, -1);
            if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0.5, 0.98, 0.5));
        }
        if (this.tickCount % 40 == 0) supply(level);
    }

    /** Hands out supplies to soldiers close by, and stocks up when at home. */
    private void supply(ServerLevel level) {
        Kingdom k = kingdomId == null ? null : KingdomData.get(level.getServer()).byId(kingdomId);
        if (k == null) return;
        if (k.hall.isPresent() && k.hallDim.equals(KingdomData.dim(level)) && k.hall.get().distSqr(blockPosition()) < 20 * 20) {
            var store = Storehouse.containers(level, k);
            topUp(store, Items.ARROW, 64);
            topUp(store, ModContent.BULLET.get(), 32);
            if (count(s -> s.has(DataComponents.FOOD)) < 16) {
                ItemStack food = Storehouse.take(store, s -> s.has(DataComponents.FOOD), 16 - count(s -> s.has(DataComponents.FOOD)));
                if (!food.isEmpty()) cargo.addItem(food);
            }
        }
        for (Citizen c : level.getEntitiesOfClass(Citizen.class, new AABB(blockPosition()).inflate(8), c -> c.sameKingdom(kingdomId) && c.isAlive())) {
            if (c.needsAmmo()) {
                boolean musket = c.job() == Job.MUSKETEER;
                int want = Citizen.MAX_AMMO - c.ammo();
                int have = take(musket ? ModContent.BULLET.get() : Items.ARROW, musket ? (want + 1) / 2 : want);
                c.resupply(musket ? have * 2 : have);
            }
            if (c.getHealth() < c.getMaxHealth() - 4) {
                ItemStack meal = removeOne(s -> s.has(DataComponents.FOOD));
                if (!meal.isEmpty()) {
                    c.heal(6);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 1.8, c.getZ(), 3, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }

    private int count(java.util.function.Predicate<ItemStack> p) {
        int n = 0;
        for (ItemStack s : cargo.getItems()) if (!s.isEmpty() && p.test(s)) n += s.getCount();
        return n;
    }

    private void topUp(java.util.List<net.minecraft.world.Container> store, net.minecraft.world.item.Item item, int target) {
        int have = count(s -> s.is(item));
        if (have >= target) return;
        ItemStack got = Storehouse.take(store, s -> s.is(item), target - have);
        if (!got.isEmpty()) cargo.addItem(got);
    }

    private int take(net.minecraft.world.item.Item item, int n) {
        int got = 0;
        for (int i = 0; i < cargo.getContainerSize() && got < n; i++) {
            ItemStack s = cargo.getItem(i);
            if (!s.is(item)) continue;
            int t = Math.min(n - got, s.getCount());
            s.shrink(t);
            got += t;
        }
        return got;
    }

    private ItemStack removeOne(java.util.function.Predicate<ItemStack> p) {
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack s = cargo.getItem(i);
            if (!s.isEmpty() && p.test(s)) return s.split(1);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;
        Kingdom k = kingdomId == null ? null : KingdomData.get(this.level().getServer()).byId(kingdomId);
        if (k != null && !k.isMember(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("A supply cart of ").append(Realm.title(k)));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            breakApart();
            return InteractionResult.SUCCESS_SERVER;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, cargo),
                Component.literal("Supply Cart")));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void breakApart() {
        if (!(this.level() instanceof ServerLevel level)) return;
        for (ItemStack s : cargo.removeAllItems()) spawnAtLocation(level, s);
        spawnAtLocation(level, new ItemStack(ModContent.SUPPLY_CART_ITEM.get()));
        if (ponyId != null && level.getEntity(ponyId) instanceof BreedHorse pony) pony.setCartId(null);
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player || amount > 8) breakApart();
        return true;
    }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) { return false; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        if (ponyId != null) out.putString("Pony", ponyId.toString());
        if (kingdomId != null) out.putString("Kingdom", kingdomId.toString());
        cargo.storeAsItemList(out.list("Cargo", ItemStack.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        ponyId = in.getString("Pony").map(SupplyCart::uuid).orElse(null);
        kingdomId = in.getString("Kingdom").map(SupplyCart::uuid).orElse(null);
        in.list("Cargo", ItemStack.CODEC).ifPresent(cargo::fromItemList);
    }

    private static @Nullable UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** For messages. */
    public static Component describe(SupplyCart c) {
        return Component.literal("Supply cart: " + c.count(s -> s.is(Items.ARROW)) + " arrows, " + c.count(s -> s.is(ModContent.BULLET.get()))
                + " shot, " + c.count(s -> s.has(DataComponents.FOOD)) + " food").withStyle(ChatFormatting.GRAY);
    }
}
