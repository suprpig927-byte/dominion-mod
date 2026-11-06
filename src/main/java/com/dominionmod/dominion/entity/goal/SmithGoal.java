package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Storehouse;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Blacksmiths work at an anvil near their post. Each job takes a while: smelt raw ore from the
 * storehouse into ingots (with coal), or forge iron armor and shields, which go straight to a
 * soldier who needs them, or into the storehouse.
 */
public class SmithGoal extends Goal {
    private static final int WORK_TICKS = 300;
    private final Citizen c;
    private BlockPos anvil, stand;
    private int ticks, hammer;

    public SmithGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (c.job() != Job.BLACKSMITH || c.order() != Order.DUTY || !c.level().isBrightOutside() || c.tickCount % 40 != 0) return false;
        anvil = null;
        BlockPos post = c.post();
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(post.offset(-16, -4, -16), post.offset(16, 4, 16))) {
            if (c.level().getBlockState(p).is(BlockTags.ANVIL)) {
                double d = p.distSqr(c.blockPosition());
                if (d < best) {
                    best = d;
                    anvil = p.immutable();
                }
            }
        }
        return anvil != null && hasWork();
    }

    private boolean hasWork() {
        if (!(c.level() instanceof ServerLevel level)) return false;
        Kingdom k = c.kingdom();
        if (k == null) return false;
        var store = Storehouse.containers(level, k);
        return canSmelt(store) || nextForge(store) != null;
    }

    @Override
    public void start() {
        ticks = 0;
        hammer = 0;
        stand = Spots.standNear(c.level(), anvil, c);
        c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
        c.markWorked();
    }

    @Override
    public boolean canContinueToUse() {
        return anvil != null && c.job() == Job.BLACKSMITH && c.order() == Order.DUTY && c.getTarget() == null && ticks < 2400;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (anvil == null || !(c.level() instanceof ServerLevel level)) return;
        ticks++;
        if (c.distanceToSqr(Vec3.atCenterOf(anvil)) > 3.0 * 3.0) {
            if (ticks % 40 == 0) c.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.55);
            return;
        }
        c.getNavigation().stop();
        c.getLookControl().setLookAt(Vec3.atCenterOf(anvil));
        if (ticks % 20 == 0) {
            c.swingForAttack(InteractionHand.MAIN_HAND);
            level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35f, 0.9f + c.getRandom().nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.LAVA, anvil.getX() + 0.5, anvil.getY() + 1.0, anvil.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0);
        }
        if (++hammer < WORK_TICKS) return;
        hammer = 0;
        c.markWorked();
        Kingdom k = c.kingdom();
        if (k == null) return;
        var store = Storehouse.containers(level, k);
        if (canSmelt(store)) smelt(store);
        else forge(level, k, store);
        if (!hasWork()) anvil = null;
    }

    // ------------------------------------------------------------------ smelting

    private static final Item[][] ORES = {{Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_GOLD, Items.GOLD_INGOT}, {Items.RAW_COPPER, Items.COPPER_INGOT}};

    private static boolean canSmelt(List<Container> store) {
        if (Storehouse.count(store, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) == 0) return false;
        for (Item[] o : ORES) if (Storehouse.count(store, s -> s.is(o[0])) > 0) return true;
        return false;
    }

    /** One coal smelts up to 8 ore. */
    private static void smelt(List<Container> store) {
        Storehouse.take(store, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1);
        int left = 8;
        for (Item[] o : ORES) {
            if (left <= 0) break;
            ItemStack raw = Storehouse.take(store, s -> s.is(o[0]), left);
            if (raw.isEmpty()) continue;
            left -= raw.getCount();
            Storehouse.insert(store, new ItemStack(o[1], raw.getCount()));
        }
    }

    // ------------------------------------------------------------------ forging

    private record Recipe(Item item, int iron, int planks, EquipmentSlot slot) {}

    private static final Recipe[] RECIPES = {
            new Recipe(Items.IRON_CHESTPLATE, 8, 0, EquipmentSlot.CHEST),
            new Recipe(Items.IRON_HELMET, 5, 0, EquipmentSlot.HEAD),
            new Recipe(Items.IRON_LEGGINGS, 7, 0, EquipmentSlot.LEGS),
            new Recipe(Items.IRON_BOOTS, 4, 0, EquipmentSlot.FEET),
            new Recipe(Items.SHIELD, 1, 6, EquipmentSlot.OFFHAND)};

    /** The first piece some soldier still lacks that we have the iron for. */
    private Recipe nextForge(List<Container> store) {
        int iron = Storehouse.count(store, s -> s.is(Items.IRON_INGOT));
        int planks = Storehouse.count(store, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
        for (Recipe r : RECIPES) {
            if (iron < r.iron || planks < r.planks) continue;
            if (soldierNeeding(r) != null) return r;
        }
        return null;
    }

    private Citizen soldierNeeding(Recipe r) {
        for (Citizen s : c.level().getEntitiesOfClass(Citizen.class, c.getBoundingBox().inflate(96),
                s -> s.sameKingdom(c.kingdomId()) && s.job().soldier && s.job() != Job.WIZARD && s.job() != Job.MONK)) {
            if (r.slot == EquipmentSlot.OFFHAND && s.job() != Job.MILITIA) continue;
            if (s.getItemBySlot(r.slot).isEmpty()) return s;
        }
        return null;
    }

    private void forge(ServerLevel level, Kingdom k, List<Container> store) {
        Recipe r = nextForge(store);
        if (r == null) return;
        Storehouse.take(store, s -> s.is(Items.IRON_INGOT), r.iron);
        if (r.planks > 0) Storehouse.take(store, s -> s.is(net.minecraft.tags.ItemTags.PLANKS), r.planks);
        ItemStack made = new ItemStack(r.item);
        Citizen who = soldierNeeding(r);
        if (who != null) {
            who.setItemSlot(r.slot, made);
            who.setGuaranteedDrop(r.slot);
            Citizen.tellKingdom(level, k, net.minecraft.network.chat.Component.literal(c.baseName() + " forged " + made.getHoverName().getString()
                    + " for " + who.baseName() + ".").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            Storehouse.insert(store, made);
        }
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }
}
