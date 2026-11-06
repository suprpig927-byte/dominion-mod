package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Order;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Storehouse;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/** Workers carry what they've gathered back to the storehouse chests by the Town Hall. */
public class DepositGoal extends Goal {
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private final Citizen c;
    private BlockPos hall;
    private int ticks, cooldown;

    public DepositGoal(Citizen c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!c.job().worker() || c.order() != Order.DUTY || c.getTarget() != null) return false;
        if (--cooldown > 0) return false;
        cooldown = 40;
        int haul = c.haulCount();
        boolean evening = !c.level().isBrightOutside();
        if (!(c.inventoryFull() && haul > 0) && haul < 48 && !(evening && haul > 0)) return false;
        Kingdom k = c.kingdom();
        if (k == null || k.hall.isEmpty() || !k.hallDim.equals(KingdomData.dim(c.level()))) return false;
        hall = k.hall.get();
        return c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hall)) < 160 * 160;
    }

    @Override
    public void start() {
        ticks = 0;
        c.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 0.55);
    }

    @Override
    public boolean canContinueToUse() {
        return hall != null && ticks < 1200 && c.getTarget() == null && c.order() == Order.DUTY && c.haulCount() > 0;
    }

    @Override
    public void tick() {
        if (hall == null) return;
        ticks++;
        if (c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hall)) > 4.5 * 4.5) {
            if (ticks % 40 == 0) c.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 0.55);
            return;
        }
        if (!(c.level() instanceof ServerLevel level)) return;
        Kingdom k = c.kingdom();
        if (k == null) return;
        var store = Storehouse.containers(level, k);
        if (store.isEmpty()) {
            long day = level.getGameTime() / Citizen.DAY;
            if (!Long.valueOf(day).equals(WARNED.get(k.id))) {
                WARNED.put(k.id, day);
                Citizen.tellKingdom(level, k, Component.literal("Your workers have nowhere to put their goods. Place chests or barrels within "
                        + Storehouse.RADIUS + " blocks of your Town Hall.").withStyle(ChatFormatting.YELLOW));
            }
            hall = null;
            cooldown = 600;
            return;
        }
        for (ItemStack s : c.takeHaul()) {
            ItemStack left = Storehouse.insert(store, s);
            if (!left.isEmpty()) c.stash(level, left);
        }
        hall = null;
        cooldown = 100;
    }

    @Override
    public void stop() {
        hall = null;
        c.getNavigation().stop();
    }
}
