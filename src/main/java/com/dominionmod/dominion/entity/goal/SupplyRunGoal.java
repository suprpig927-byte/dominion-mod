package com.dominionmod.dominion.entity.goal;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.wild.BreedHorse;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A pony with a supply cart follows the kingdom's soldiers who are out in the field, keeping a few
 * paces behind the main body of them, and goes home to the Town Hall when nobody is out.
 */
public class SupplyRunGoal extends Goal {
    private static final Identifier HAUL = com.dominionmod.dominion.DominionMod.id("hauling");
    private final BreedHorse pony;
    private int think;

    public SupplyRunGoal(BreedHorse pony) {
        this.pony = pony;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return pony.cartId() != null && pony.kingdomId() != null && !pony.isVehicle() && !pony.isLeashed() && !pony.isBaby();
    }

    @Override
    public void start() {
        var speed = pony.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(HAUL)) speed.addTransientModifier(new AttributeModifier(HAUL, 0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public void stop() {
        var speed = pony.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(HAUL)) speed.removeModifier(HAUL);
    }

    @Override
    public void tick() {
        if (--think > 0 || pony.level().getServer() == null) return;
        think = 20;
        Kingdom k = KingdomData.get(pony.level().getServer()).byId(pony.kingdomId());
        if (k == null) return;
        List<Citizen> out = pony.level().getEntitiesOfClass(Citizen.class, new AABB(pony.blockPosition()).inflate(96),
                c -> c.sameKingdom(k.id) && c.job().soldier && c.inField() && c.isAlive());
        Vec3 goal = null;
        double stop = 5;
        if (!out.isEmpty()) {
            double x = 0, y = 0, z = 0;
            for (Citizen c : out) {
                x += c.getX();
                y += c.getY();
                z += c.getZ();
            }
            goal = new Vec3(x / out.size(), y / out.size(), z / out.size());
        } else if (k.hall.isPresent() && k.hallDim.equals(KingdomData.dim(pony.level()))) {
            goal = Vec3.atBottomCenterOf(k.hall.get().south(6));
            stop = 10;
        }
        if (goal == null || pony.position().distanceTo(goal) < stop) {
            pony.getNavigation().stop();
            return;
        }
        Vec3 d = goal.subtract(pony.position());
        double len = d.length(), leg = Math.min(len - stop + 1, 16);
        Vec3 to = pony.position().add(d.scale(leg / len));
        pony.getNavigation().moveTo(to.x, goal.y, to.z, 1.0);
    }
}
