package com.dominionmod.dominion.item;

import com.dominionmod.dominion.entity.Cavalry;
import com.dominionmod.dominion.entity.SupplyCart;
import com.dominionmod.dominion.entity.wild.BreedHorse;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.registry.ModContent;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Hitch it to a tamed Shetland pony: right-click the pony with it. */
public class SupplyCartItem extends Item {
    public SupplyCartItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!(target instanceof BreedHorse pony) || pony.breed != BreedHorse.Breed.SHETLAND) {
            player.sendOverlayMessage(Component.literal("Only a Shetland pony can pull a supply cart.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (pony.cartId() != null) {
            player.sendOverlayMessage(Component.literal("That pony already pulls a cart.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        KingdomData d = KingdomData.get(level.getServer());
        Kingdom k = d.byPlayer(player.getUUID());
        UUID owner = Cavalry.kingdomOf(pony);
        if (k == null || !pony.isTamed() || owner != null && !owner.equals(k.id)) {
            player.sendOverlayMessage(Component.literal("Tame the pony first (and found a kingdom).").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (pony.isBaby()) {
            player.sendOverlayMessage(Component.literal("That foal is too small to pull a cart.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        SupplyCart cart = ModContent.SUPPLY_CART.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (cart == null) return InteractionResult.FAIL;
        cart.snapTo(pony.getX(), pony.getY(), pony.getZ(), pony.getYRot(), 0);
        cart.hitch(pony, k.id);
        level.addFreshEntity(cart);
        if (!player.isCreative()) stack.shrink(1);
        player.sendSystemMessage(Component.literal("The pony is hitched to the supply cart. ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("It follows your soldiers when they're out in the field and keeps them in arrows, shot and food; "
                        + "at home it stocks up from the storehouse. Right-click the cart to load it yourself.").withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS_SERVER;
    }
}
