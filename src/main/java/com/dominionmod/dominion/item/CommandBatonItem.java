package com.dominionmod.dominion.item;

import com.dominionmod.dominion.command.Orders;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Realm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Command Baton.
 * <ul>
 *   <li>Right-click a citizen: select / deselect them. Sneak: select every soldier within 24 blocks.</li>
 *   <li>Right-click an enemy: selected soldiers charge it.</li>
 *   <li>Right-click a block: soldiers march there and hold; workers make it their work spot.</li>
 *   <li>Right-click the air: give the current order. Sneak: switch order (Rally / Hold / Return to duty / Clear).</li>
 * </ul>
 */
public class CommandBatonItem extends Item {
    public CommandBatonItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Kingdom k = Realm.of(sp);
        if (k == null) {
            sp.sendOverlayMessage(Realm.err("You need a kingdom to command anyone."));
            return InteractionResult.FAIL;
        }
        if (target instanceof Citizen c && c.sameKingdom(k.id)) {
            if (sp.isShiftKeyDown()) Orders.selectAllSoldiersNear(sp);
            else Orders.toggle(sp, c);
        } else {
            Orders.charge(sp, target);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown()) {
            Orders.cycleMode(sp);
            return InteractionResult.SUCCESS_SERVER;
        }
        Orders.orderTo(sp, ctx.getClickedPos());
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown()) Orders.cycleMode(sp);
        else Orders.giveOrder(sp);
        return InteractionResult.SUCCESS_SERVER;
    }
}
