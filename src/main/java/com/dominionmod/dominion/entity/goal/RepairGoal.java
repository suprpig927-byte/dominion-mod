package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Armies;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Once the fighting is over, builders and engineers go round the walls putting back every block a
 * siege knocked out, with stone and timber from the storehouse (any stone for stone, any planks
 * for planks, if the exact block isn't there).
 */
public class RepairGoal extends Goal {
    private static final Map<java.util.UUID, Long> SHORT = new HashMap<>();
    private final Citizen c;
    private KingdomData.@Nullable Ruin ruin;
    private int ticks, cooldown;

    public RepairGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!c.job().builds() || c.order() != Order.DUTY || c.getTarget() != null || !(c.level() instanceof ServerLevel level)) return false;
        if (--cooldown > 0) return false;
        cooldown = 60;
        Kingdom k = c.kingdom();
        if (k == null || Armies.underAttack(level, k)) return false;
        Long short_ = SHORT.get(k.id);
        if (short_ != null && level.getGameTime() - short_ < 1200) return false; // out of stone and timber: back to other work for a while
        KingdomData d = Realm.data(level);
        String dim = KingdomData.dim(level);
        double best = 96 * 96;
        ruin = null;
        for (KingdomData.Ruin r : d.ruins) {
            if (!r.kingdom().equals(k.id) || !r.dim().equals(dim)) continue;
            double dist = c.distanceToSqr(Vec3.atCenterOf(r.pos()));
            if (dist < best) {
                best = dist;
                ruin = r;
            }
        }
        return ruin != null;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ruin != null && ticks < 600 && c.getTarget() == null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ruin == null || !(c.level() instanceof ServerLevel level)) return;
        ticks++;
        Vec3 at = Vec3.atCenterOf(ruin.pos());
        if (c.distanceToSqr(at) > 4.5 * 4.5) {
            if (ticks % 30 == 1) c.getNavigation().moveTo(at.x, at.y, at.z, 0.55);
            if (ticks < 400) return;
        }
        c.getLookControl().setLookAt(at);
        if (ticks % 10 != 0) return;
        KingdomData d = Realm.data(level);
        Kingdom k = c.kingdom();
        if (k == null) return;
        if (!level.getBlockState(ruin.pos()).canBeReplaced()) { // someone else filled the gap
            d.ruins.remove(ruin);
            d.markChanged();
            ruin = null;
            return;
        }
        Item exact = ruin.state().getBlock().asItem();
        var store = Storehouse.containers(level, k);
        ItemStack got = exact == Items.AIR ? ItemStack.EMPTY : Storehouse.take(store, s -> s.is(exact), 1);
        if (got.isEmpty()) {
            boolean stony = ruin.state().is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || exact.toString().contains("stone")
                    || exact.toString().contains("brick") || exact.toString().contains("deepslate") || exact.toString().contains("mud");
            got = stony ? Storehouse.take(store, s -> s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.COBBLED_DEEPSLATE), 1)
                    : Storehouse.take(store, s -> s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS), 1);
        }
        if (got.isEmpty()) {
            Long warned = SHORT.get(k.id);
            if (warned == null || level.getGameTime() - warned > 6000) {
                SHORT.put(k.id, level.getGameTime());
                Citizen.tellKingdom(level, k, net.minecraft.network.chat.Component.literal(c.baseName() + " can't mend the walls: the storehouse is out of "
                        + "stone and timber.").withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            ruin = null;
            return;
        }
        c.swingForAttack(InteractionHand.MAIN_HAND);
        level.setBlock(ruin.pos(), Block.updateFromNeighbourShapes(ruin.state(), level, ruin.pos()), Block.UPDATE_ALL);
        level.playSound(null, ruin.pos(), ruin.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.0f);
        d.ruins.remove(ruin);
        d.markChanged();
        c.markWorked();
        ruin = null;
    }
}
