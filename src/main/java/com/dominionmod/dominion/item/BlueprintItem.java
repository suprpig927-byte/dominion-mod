package com.dominionmod.dominion.item;

import com.dominionmod.dominion.build.Construction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A blueprint for one building or wall. Right-click the ground to mark it out, sneak + right-click
 * to rotate, right-click the same spot again to mark a construction site. For walls, click both ends.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        sp.sendSystemMessage(Construction.use(sp, ctx.getItemInHand(), ctx.getClickedPos()));
        return InteractionResult.SUCCESS_SERVER;
    }
}
